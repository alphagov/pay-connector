package uk.gov.pay.connector.charge.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.resource.ChargesApiResource;
import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.chargeevents.dao.ChargeEventDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.api.ExternalTransactionState;
import uk.gov.pay.connector.charge.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.model.builder.PatchRequestBuilder;
import uk.gov.pay.connector.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.PersistedCard;
import uk.gov.pay.connector.model.domain.TokenEntity;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.pay.connector.util.charge.CorporateCardSurchargeCalculator;

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
import static uk.gov.pay.connector.charge.model.ChargeResponse.aChargeResponseBuilder;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.NumbersInStringsSanitizer.sanitize;

public class ChargeService {
    private static final Logger logger = LoggerFactory.getLogger(ChargeService.class);

    private static final List<ChargeStatus> CURRENT_STATUSES_ALLOWING_UPDATE_TO_NEW_STATUS = newArrayList(CREATED, ENTERING_CARD_DETAILS);

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final CardTypeDao cardTypeDao;
    private final TokenDao tokenDao;
    private final GatewayAccountDao gatewayAccountDao;
    private final LinksConfig linksConfig;
    private final PaymentProviders providers;

    @Inject
    public ChargeService(TokenDao tokenDao, ChargeDao chargeDao, ChargeEventDao chargeEventDao,
                         CardTypeDao cardTypeDao, GatewayAccountDao gatewayAccountDao,
                         ConnectorConfiguration config, PaymentProviders providers) {
        this.tokenDao = tokenDao;
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.cardTypeDao = cardTypeDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.linksConfig = config.getLinks();
        this.providers = providers;
    }

    @Transactional
    public Optional<ChargeResponse> create(Map<String, String> chargeRequest, Long accountId, UriInfo uriInfo) {
        return gatewayAccountDao.findById(accountId).map(gatewayAccount -> {

            if (gatewayAccount.isLive() && !chargeRequest.get("return_url").startsWith("https://")) {
                logger.info(String.format("Gateway account %d is LIVE, but is configured to use a non-https return_url", accountId));
            }

            String chargeRequestLanguage = chargeRequest.get("language");
            SupportedLanguage language = chargeRequestLanguage != null
                    ? SupportedLanguage.fromIso639AlphaTwoCode(chargeRequestLanguage)
                    : SupportedLanguage.ENGLISH;

            ChargeEntity chargeEntity = new ChargeEntity(new Long(chargeRequest.get("amount")),
                    chargeRequest.get("return_url"),
                    chargeRequest.get("description"),
                    ServicePaymentReference.of(chargeRequest.get("reference")),
                    gatewayAccount,
                    chargeRequest.get("email"),
                    language,
                    Boolean.valueOf(chargeRequest.getOrDefault("delayed_capture", "false")),
                    null); //TODO add logic
            chargeDao.persist(chargeEntity);

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
    public Optional<ChargeEntity> updateCharge(String chargeId, PatchRequestBuilder.PatchRequest chargePatchRequest) {
        return chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> {
                    if (chargePatchRequest.getPath().equals(ChargesApiResource.EMAIL_KEY)) {
                        chargeEntity.setEmail(sanitize(chargePatchRequest.getValue()));
                    }
                    return Optional.of(chargeEntity);
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

        T builderOfResponse = responseBuilder
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
                .withLanguage(chargeEntity.getLanguage())
                .withDelayedCapture(chargeEntity.isDelayedCapture())
                .withRefunds(buildRefundSummary(chargeEntity))
                .withSettlement(buildSettlementSummary(chargeEntity))
                .withCardDetails(persistedCard)
                .withAuth3dsData(auth3dsData)
                .withLink("self", GET, selfUriFor(uriInfo, chargeEntity.getGatewayAccount().getId(), chargeId))
                .withLink("refunds", GET, refundsUriFor(uriInfo, chargeEntity.getGatewayAccount().getId(), chargeEntity.getExternalId()));

        chargeEntity.getCorporateSurcharge().ifPresent(corporateSurcharge ->
                builderOfResponse.withCorporateCardSurcharge(corporateSurcharge)
                        .withTotalAmount(CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity)));

        ChargeStatus chargeStatus = ChargeStatus.fromString(chargeEntity.getStatus());
        if (!chargeStatus.toExternal().isFinished() && !chargeStatus.equals(ChargeStatus.AWAITING_CAPTURE_REQUEST)) {
            TokenEntity token = createNewChargeEntityToken(chargeEntity);
            Map<String, Object> params = new HashMap<>();
            params.put("chargeTokenId", token.getToken());

            return builderOfResponse
                    .withLink("next_url", GET, nextUrl(token.getToken()))
                    .withLink("next_url_post", POST, nextUrl(), APPLICATION_FORM_URLENCODED, params);
        }

        return builderOfResponse;
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
                .path("/v1/api/accounts/{accountId}/charges/{chargeId}")
                .build(accountId, chargeId);
    }

    private URI refundsUriFor(UriInfo uriInfo, Long accountId, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds")
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
