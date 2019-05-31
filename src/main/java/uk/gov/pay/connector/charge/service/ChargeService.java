package uk.gov.pay.connector.charge.service;

import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.exception.ZeroAmountNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.PrefilledCardHolderDetails;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.PersistedCard;
import uk.gov.pay.connector.charge.resource.ChargesApiResource;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.charge.util.RefundCalculator;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
import uk.gov.pay.connector.common.service.PatchRequestBuilder;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;
import uk.gov.pay.connector.wallets.WalletType;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.ChargeResponse.aChargeResponseBuilder;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.fromString;
import static uk.gov.pay.connector.common.model.domain.NumbersInStringsSanitizer.sanitize;

public class ChargeService {
    private static final Logger logger = LoggerFactory.getLogger(ChargeService.class);

    private static final List<ChargeStatus> CURRENT_STATUSES_ALLOWING_UPDATE_TO_NEW_STATUS = newArrayList(CREATED, ENTERING_CARD_DETAILS);

    private static final List<ChargeStatus> IGNORABLE_CAPTURE_STATES = ImmutableList.of(
            CAPTURE_APPROVED,
            CAPTURE_APPROVED_RETRY,
            CAPTURE_READY,
            CAPTURE_SUBMITTED,
            CAPTURED
    );

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final CardTypeDao cardTypeDao;
    private final TokenDao tokenDao;
    private final GatewayAccountDao gatewayAccountDao;
    private final LinksConfig linksConfig;
    private final CaptureProcessConfig captureProcessConfig;
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
        this.captureProcessConfig = config.getCaptureProcessConfig();
    }

    @Transactional
    public Optional<ChargeResponse> create(ChargeCreateRequest chargeRequest, Long accountId, UriInfo uriInfo) {
        return gatewayAccountDao.findById(accountId).map(gatewayAccount -> {

            if (chargeRequest.getAmount() == 0L && !gatewayAccount.isAllowZeroAmount()) {
                throw new ZeroAmountNotAllowedForGatewayAccountException(gatewayAccount.getId());
            }

            if (gatewayAccount.isLive() && !chargeRequest.getReturnUrl().startsWith("https://")) {
                logger.info(String.format("Gateway account %d is LIVE, but is configured to use a non-https return_url", accountId));
            }

            SupportedLanguage language = chargeRequest.getLanguage() != null
                    ? chargeRequest.getLanguage()
                    : SupportedLanguage.ENGLISH;

            ChargeEntity chargeEntity = new ChargeEntity(
                    chargeRequest.getAmount(),
                    chargeRequest.getReturnUrl(),
                    chargeRequest.getDescription(),
                    ServicePaymentReference.of(chargeRequest.getReference()),
                    gatewayAccount,
                    chargeRequest.getEmail(),
                    language,
                    chargeRequest.isDelayedCapture(),
                    chargeRequest.getExternalMetadata().orElse(null));

            chargeRequest.getPrefilledCardHolderDetails()
                    .map(this::createCardDetailsEntity)
                    .ifPresent(chargeEntity::setCardDetails);

            chargeDao.persist(chargeEntity);

            chargeEventDao.persistChargeEventOf(chargeEntity);
            return populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, chargeEntity).build();
        });
    }

    private CardDetailsEntity createCardDetailsEntity(PrefilledCardHolderDetails prefilledCardHolderDetails) {
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity();
        prefilledCardHolderDetails.getCardHolderName().ifPresent(cardDetailsEntity::setCardHolderName);
        prefilledCardHolderDetails.getAddress().map(AddressEntity::new).ifPresent(cardDetailsEntity::setBillingAddress);
        return cardDetailsEntity;
    }

    @Transactional
    public void abortCharge(ChargeEntity charge) {
        charge.setStatus(AUTHORISATION_ABORTED);
        chargeEventDao.persistChargeEventOf(charge);
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
                        chargeEventDao.persistChargeEventOf(chargeEntity);
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
                .withCreatedDate(chargeEntity.getCreatedDate())
                .withReturnUrl(chargeEntity.getReturnUrl())
                .withEmail(chargeEntity.getEmail())
                .withLanguage(chargeEntity.getLanguage())
                .withDelayedCapture(chargeEntity.isDelayedCapture())
                .withRefunds(buildRefundSummary(chargeEntity))
                .withSettlement(buildSettlementSummary(chargeEntity))
                .withCardDetails(persistedCard)
                .withAuth3dsData(auth3dsData)
                .withLink("self", GET, selfUriFor(uriInfo, chargeEntity.getGatewayAccount().getId(), chargeId))
                .withLink("refunds", GET, refundsUriFor(uriInfo, chargeEntity.getGatewayAccount().getId(), chargeEntity.getExternalId()))
                .withWalletType(chargeEntity.getWalletType());

        chargeEntity.getFeeAmount().ifPresent(builderOfResponse::withFee);
        chargeEntity.getExternalMetadata().ifPresent(builderOfResponse::withExternalMetadata);

        if (ChargeStatus.AWAITING_CAPTURE_REQUEST.getValue().equals(chargeEntity.getStatus())) {
            builderOfResponse.withLink("capture", POST, captureUriFor(uriInfo, chargeEntity.getGatewayAccount().getId(), chargeEntity.getExternalId()));
        }

        chargeEntity.getCorporateSurcharge().ifPresent(corporateSurcharge ->
                builderOfResponse.withCorporateCardSurcharge(corporateSurcharge)
                        .withTotalAmount(CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity)));

        // @TODO(sfount) consider if total and net columns could be calculation columns in postgres (single source of truth)
        chargeEntity.getNetAmount().ifPresent(builderOfResponse::withNetAmount);

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

    @Transactional
    public ChargeEntity updateChargePostAuthorisation(String chargeExternalId,
                                                      ChargeStatus status,
                                                      Optional<String> transactionId,
                                                      Optional<Auth3dsDetailsEntity> auth3dsDetails,
                                                      Optional<String> sessionIdentifier,
                                                      AuthCardDetails authCardDetails) {
        return chargeDao.findByExternalId(chargeExternalId).map(charge -> {
            charge.setStatus(status);

            setTransactionId(charge, transactionId);
            sessionIdentifier.ifPresent(charge::setProviderSessionId);
            auth3dsDetails.ifPresent(charge::set3dsDetails);
            CardDetailsEntity detailsEntity = buildCardDetailsEntity(authCardDetails);
            charge.setCardDetails(detailsEntity);

            chargeEventDao.persistChargeEventOf(charge);

            logger.info("Stored confirmation details for charge - charge_external_id={}",
                    chargeExternalId);

            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
    }

    @Transactional
    public ChargeEntity updateChargePostWalletAuthorisation(String chargeExternalId,
                                                            ChargeStatus status,
                                                            Optional<String> transactionId,
                                                            Optional<Auth3dsDetailsEntity> auth3dsDetails,
                                                            Optional<String> sessionIdentifier,
                                                            AuthCardDetails authCardDetails,
                                                            WalletType walletType,
                                                            String emailAddress) {
        ChargeEntity chargeEntity = updateChargePostAuthorisation(chargeExternalId, status, transactionId, auth3dsDetails, sessionIdentifier, authCardDetails);
        chargeEntity.setWalletType(walletType);
        chargeEntity.setEmail(emailAddress);
        return chargeEntity;
    }

    @Transactional
    public ChargeEntity updateChargePost3dsAuthorisation(String chargeExternalId, ChargeStatus status,
                                                         OperationType operationType,
                                                         Optional<String> transactionId) {
        return chargeDao.findByExternalId(chargeExternalId).map(charge -> {
            try {
                charge.setStatus(status);
                setTransactionId(charge, transactionId);
                chargeEventDao.persistChargeEventOf(charge);
            } catch (InvalidStateTransitionException e) {
                if (chargeIsInLockedStatus(operationType, charge)) {
                    throw new OperationAlreadyInProgressRuntimeException(operationType.getValue(), charge.getExternalId());
                }
                throw new IllegalStateRuntimeException(charge.getExternalId());
            }
            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
    }

    public ChargeEntity updateChargePostCapture(String chargeId, ChargeStatus nextStatus) {
        return chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> {
                    updateChargeStatus(chargeEntity, nextStatus);
                    return chargeEntity;
                })
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private void setTransactionId(ChargeEntity chargeEntity, Optional<String> transactionId) {
        transactionId.ifPresent(txId -> {
            if (!isBlank(txId)) {
                chargeEntity.setGatewayTransactionId(txId);
            }
        });
    }

    @Transactional
    public ChargeEntity lockChargeForProcessing(String chargeId, OperationType operationType) {
        return chargeDao.findByExternalId(chargeId).map(chargeEntity -> {
            try {

                GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();

                // Used by Sumo Logic saved search
                logger.info("Card pre-operation - charge_external_id={}, charge_status={}, account_id={}, amount={}, operation_type={}, provider={}, provider_type={}, locking_status={}",
                        chargeEntity.getExternalId(),
                        fromString(chargeEntity.getStatus()),
                        gatewayAccount.getId(),
                        chargeEntity.getAmount(),
                        operationType.getValue(),
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType(),
                        operationType.getLockingStatus());

                chargeEntity.setStatus(operationType.getLockingStatus());

            } catch (InvalidStateTransitionException e) {
                if (chargeIsInLockedStatus(operationType, chargeEntity)) {
                    throw new OperationAlreadyInProgressRuntimeException(operationType.getValue(), chargeEntity.getExternalId());
                }
                throw new IllegalStateRuntimeException(chargeEntity.getExternalId());
            }
            return chargeEntity;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    public ChargeEntity findChargeById(String chargeId) {
        return chargeDao.findByExternalId(chargeId)
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    public ChargeEntity updateChargeStatus(ChargeEntity chargeEntity, ChargeStatus chargeStatus) {
        if (chargeStatus == CAPTURED) {
            chargeEntity.setStatus(CAPTURE_SUBMITTED);
            chargeEventDao.persistChargeEventOf(chargeEntity);
            chargeEntity.setStatus(CAPTURED);
            chargeEventDao.persistChargeEventOf(chargeEntity, ZonedDateTime.now());
        } else {
            chargeEntity.setStatus(chargeStatus);
            chargeEventDao.persistChargeEventOf(chargeEntity);
        }
        return chargeEntity;
    }

    public ChargeEntity updateChargeStatus(String chargeId, ChargeStatus chargeStatus) {
        return chargeDao.findByExternalId(chargeId).map(chargeEntity ->
                updateChargeStatus(chargeEntity, chargeStatus)
        ).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    public Optional<ChargeEntity> findByProviderAndTransactionId(String paymentGatewayName, String transactionId) {
        return chargeDao.findByProviderAndTransactionId(paymentGatewayName, transactionId);
    }

    @Transactional
    public ChargeEntity markChargeAsEligibleForCapture(String externalId) {
        return chargeDao.findByExternalId(externalId).map(charge -> {
            ChargeStatus targetStatus = charge.isDelayedCapture() ? AWAITING_CAPTURE_REQUEST : CAPTURE_APPROVED;

            try {
                updateChargeStatus(charge, targetStatus);
            } catch (InvalidStateTransitionException e) {
                throw new IllegalStateRuntimeException(charge.getExternalId());
            }

            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));
    }

    @Transactional
    public ChargeEntity markChargeAsCaptureApproved(String externalId) {
        return chargeDao.findByExternalId(externalId).map(charge -> {

            ChargeStatus currentStatus = fromString(charge.getStatus());

            if (chargeCanBeSkipped(charge)) {
                logger.info("Skipping charge [charge_external_id={}] with status [{}] from marking as CAPTURE APPROVED", currentStatus, externalId);
                return charge;
            }

            try {
                updateChargeStatus(charge, CAPTURE_APPROVED);
            } catch (InvalidStateTransitionException e) {
                throw new ConflictRuntimeException(charge.getExternalId(),
                        format("attempt to perform delayed capture on charge not in %s state.", AWAITING_CAPTURE_REQUEST));
            }

            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));
    }

    public boolean isChargeRetriable(String externalId) {
        int numberOfChargeRetries = chargeDao.countCaptureRetriesForChargeExternalId(externalId);
        return numberOfChargeRetries <= captureProcessConfig.getMaximumRetries();
    }

    public boolean isChargeCaptured(String externalId) {
        ChargeEntity charge = findChargeById(externalId);
        return ChargeStatus.fromString(charge.getStatus()) == CAPTURED;
    }

    private CardDetailsEntity buildCardDetailsEntity(AuthCardDetails authCardDetails) {
        CardDetailsEntity detailsEntity = new CardDetailsEntity();
        detailsEntity.setCardBrand(sanitize(authCardDetails.getCardBrand()));
        detailsEntity.setCardHolderName(sanitize(authCardDetails.getCardHolder()));
        detailsEntity.setExpiryDate(authCardDetails.getEndDate());
        if (hasFullCardNumber(authCardDetails)) { // Apple Pay etc. don’t give us a full card number, just the last four digits here
            detailsEntity.setFirstDigitsCardNumber(FirstDigitsCardNumber.of(StringUtils.left(authCardDetails.getCardNo(), 6)));
        }
        detailsEntity.setLastDigitsCardNumber(LastDigitsCardNumber.of(StringUtils.right(authCardDetails.getCardNo(), 4)));

        if (authCardDetails.getAddress().isPresent())
            detailsEntity.setBillingAddress(new AddressEntity(authCardDetails.getAddress().get()));

        return detailsEntity;
    }

    private boolean hasFullCardNumber(AuthCardDetails authCardDetails) {
        return authCardDetails.getCardNo().length() > 6;
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
        refund.setAmountSubmitted(RefundCalculator.getRefundedAmount(charge));
        refund.setAmountAvailable(RefundCalculator.getTotalAmountAvailableToBeRefunded(charge));
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

    private URI captureUriFor(UriInfo uriInfo, Long accountId, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path("/v1/api/accounts/{accountId}/charges/{chargeId}/capture")
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

    private boolean chargeIsInLockedStatus(OperationType operationType, ChargeEntity chargeEntity) {
        return operationType.getLockingStatus().equals(ChargeStatus.fromString(chargeEntity.getStatus()));
    }

    private boolean chargeCanBeSkipped(ChargeEntity charge) {
        return IGNORABLE_CAPTURE_STATES.contains(ChargeStatus.fromString(charge.getStatus()));
    }
}
