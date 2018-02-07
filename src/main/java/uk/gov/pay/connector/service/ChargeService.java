package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.ChargeResponse;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.api.ExternalTransactionState;
import uk.gov.pay.connector.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.model.builder.PatchRequestBuilder;
import uk.gov.pay.connector.model.domain.CardTypeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.PersistedCard;
import uk.gov.pay.connector.model.domain.TokenEntity;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;
import uk.gov.pay.connector.resources.ChargesApiResource;
import uk.gov.pay.connector.util.DateTimeUtils;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static uk.gov.pay.connector.model.ChargeResponse.aChargeResponseBuilder;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.NumbersInStringsSanitizer.sanitize;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.REFUNDS_API_PATH;

public class ChargeService {

    private static final List<ChargeStatus> CURRENT_STATUSES_ALLOWING_UPDATE_TO_NEW_STATUS = newArrayList(CREATED, ENTERING_CARD_DETAILS);

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final CardTypeDao cardTypeDao;
    private final TokenDao tokenDao;
    private final GatewayAccountDao gatewayAccountDao;
    private final LinksConfig linksConfig;
    private final PaymentProviders providers;
    private final PaymentRequestDao paymentRequestDao;
    private final ChargeStatusUpdater chargeStatusUpdater;

    @Inject
    public ChargeService(TokenDao tokenDao, ChargeDao chargeDao, ChargeEventDao chargeEventDao,
                         CardTypeDao cardTypeDao, GatewayAccountDao gatewayAccountDao,
                         ConnectorConfiguration config, PaymentProviders providers,
                         PaymentRequestDao paymentRequestDao,
                         ChargeStatusUpdater chargeStatusUpdater) {
        this.tokenDao = tokenDao;
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.cardTypeDao = cardTypeDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.linksConfig = config.getLinks();
        this.providers = providers;
        this.paymentRequestDao = paymentRequestDao;
        this.chargeStatusUpdater = chargeStatusUpdater;
    }

    @Transactional
    public Optional<ChargeResponse> create(Map<String, String> chargeRequest, Long accountId, UriInfo uriInfo) {
        return gatewayAccountDao.findById(accountId).map(gatewayAccount -> {
            ChargeEntity chargeEntity = new ChargeEntity(new Long(chargeRequest.get("amount")),
                    chargeRequest.get("return_url"),
                    chargeRequest.get("description"),
                    chargeRequest.get("reference"),
                    gatewayAccount,
                    chargeRequest.get("email")
            );
            chargeDao.persist(chargeEntity);

            PaymentRequestEntity paymentRequestEntity =
                    PaymentRequestEntity.from(chargeEntity, ChargeTransactionEntity.from(chargeEntity));
            paymentRequestDao.persist(paymentRequestEntity);

            //todo: create a TransactionEventEntity here in future stories

            chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());
            return Optional.of(populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, chargeEntity).build());
        }).orElseGet(Optional::empty);
    }

    @Transactional
    public Optional<ChargeResponse> findChargeForAccount(String chargeId, Long accountId, UriInfo uriInfo) {
        return chargeDao
                .findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, chargeEntity).build());
    }

    @Transactional
    public Optional<PaymentRequestEntity> updateEmail(String externalId, PatchRequestBuilder.PatchRequest chargePatchRequest) {
        return paymentRequestDao.findByExternalId(externalId)
                .map(paymentRequestEntity -> {
                    switch (chargePatchRequest.getPath()) {
                        case ChargesApiResource.EMAIL_KEY:
                            final String sanitizedEmail = sanitize(chargePatchRequest.getValue());
                            paymentRequestEntity.getChargeTransaction().setEmail(sanitizedEmail);

                            chargeDao.findByExternalId(externalId).ifPresent(chargeEntity ->
                                    chargeEntity.setEmail(sanitizedEmail)
                            );
                    }
                    return Optional.of(paymentRequestEntity);
                })
                .orElseGet(Optional::empty);
    }

    @Transactional
    public Optional<ChargeEntity> updateFromInitialStatus(String externalId, ChargeStatus newChargeStatus) {
        return chargeDao.findByExternalId(externalId)
                .map(chargeEntity -> {
                    final ChargeStatus oldChargeStatus = ChargeStatus.fromString(chargeEntity.getStatus());
                    if (CURRENT_STATUSES_ALLOWING_UPDATE_TO_NEW_STATUS.contains(oldChargeStatus)) {
                        chargeEntity.setStatus(newChargeStatus);
                        chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());
                        chargeStatusUpdater.updateChargeTransactionStatus(externalId, newChargeStatus);
                        return Optional.of(chargeEntity);
                    }
                    return Optional.<ChargeEntity>empty();
                }).orElse(Optional.empty());
    }

    public <T extends AbstractChargeResponseBuilder<T, R>, R> AbstractChargeResponseBuilder<T, R> populateResponseBuilderWith(AbstractChargeResponseBuilder<T, R> responseBuilder, UriInfo uriInfo, ChargeEntity chargeEntity) {
        String chargeId = chargeEntity.getExternalId();
        PersistedCard persistedCard = null;
        if (chargeEntity.getCardDetails() != null) {
            persistedCard = chargeEntity.getCardDetails().toCard();
            persistedCard.setCardBrand(findCardBrandLabel(chargeEntity.getCardDetails().getCardBrand()).orElse(""));
        }

        ChargeResponse.Auth3dsData auth3dsData = null;
        if (chargeEntity.get3dsDetails() != null) {
            auth3dsData = new ChargeResponse.Auth3dsData();
            auth3dsData.setPaRequest(chargeEntity.get3dsDetails().getPaRequest());
            auth3dsData.setIssuerUrl(chargeEntity.get3dsDetails().getIssuerUrl());
        }
        ExternalChargeState externalChargeState = ChargeStatus.fromString(chargeEntity.getStatus()).toExternal();

        T reponseBuilder = responseBuilder
                .withChargeId(chargeId)
                .withAmount(chargeEntity.getAmount())
                .withReference(chargeEntity.getReference())
                .withDescription(chargeEntity.getDescription())
                .withState(new ExternalTransactionState(externalChargeState.getStatus(), externalChargeState.isFinished(), externalChargeState.getCode(), externalChargeState.getMessage()))
                .withGatewayTransactionId(chargeEntity.getGatewayTransactionId())
                .withProviderName(chargeEntity.getGatewayAccount().getGatewayName())
                .withCreatedDate(DateTimeUtils.toUTCDateTimeString(chargeEntity.getCreatedDate()))
                .withReturnUrl(chargeEntity.getReturnUrl())
                .withEmail(chargeEntity.getEmail())
                .withRefunds(buildRefundSummary(chargeEntity))
                .withSettlement(buildSettlementSummary(chargeEntity))
                .withCardDetails(persistedCard)
                .withAuth3dsData(auth3dsData)
                .withLink("self", GET, selfUriFor(uriInfo, chargeEntity.getGatewayAccount().getId(), chargeId))
                .withLink("refunds", GET, refundsUriFor(uriInfo, chargeEntity.getGatewayAccount().getId(), chargeEntity.getExternalId()));

        if (!ChargeStatus.fromString(chargeEntity.getStatus()).toExternal().isFinished()) {
            TokenEntity token = createNewChargeEntityToken(chargeEntity);
            return reponseBuilder
                    .withLink("next_url", GET, nextUrl(token.getToken()))
                    .withLink("next_url_post", POST, nextUrl(), APPLICATION_FORM_URLENCODED, new HashMap<String, Object>() {{
                        put("chargeTokenId", token.getToken());
                    }});
        }

        return reponseBuilder;
    }

    private TokenEntity createNewChargeEntityToken(ChargeEntity chargeEntity) {
        TokenEntity token = TokenEntity.generateNewTokenFor(chargeEntity);
        tokenDao.persist(token);
        return token;
    }

    private Optional<String> findCardBrandLabel(String cardBrand) {
        if (cardBrand == null) {
            return Optional.empty();
        }

        return cardTypeDao.findByBrand(cardBrand)
                .stream()
                .findFirst()
                .map(CardTypeEntity::getLabel);
    }

    private ChargeResponse.RefundSummary buildRefundSummary(ChargeEntity charge) {
        ChargeResponse.RefundSummary refund = new ChargeResponse.RefundSummary();
        refund.setStatus(providers.byName(charge.getPaymentGatewayName()).getExternalChargeRefundAvailability(charge).getStatus());
        refund.setAmountSubmitted(charge.getRefundedAmount());
        refund.setAmountAvailable(charge.getTotalAmountToBeRefunded());
        return refund;
    }

    private ChargeResponse.SettlementSummary buildSettlementSummary(ChargeEntity charge) {
        ChargeResponse.SettlementSummary settlement = new ChargeResponse.SettlementSummary();

        settlement.setCaptureSubmitTime(charge.getCaptureSubmitTime());
        settlement.setCapturedTime(charge.getCapturedTime());

        return settlement;
    }

    private URI selfUriFor(UriInfo uriInfo, Long accountId, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(CHARGE_API_PATH)
                .build(accountId, chargeId);
    }

    private URI refundsUriFor(UriInfo uriInfo, Long accountId, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(REFUNDS_API_PATH)
                .build(accountId, chargeId);
    }

    private URI nextUrl(String tokenId) {
        return UriBuilder.fromUri(linksConfig.getFrontendUrl())
                .path("secure")
                .path(tokenId)
                .build();
    }

    private URI nextUrl() {
        return UriBuilder.fromUri(linksConfig.getFrontendUrl())
                .path("secure")
                .build();
    }
}
