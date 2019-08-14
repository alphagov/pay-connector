package uk.gov.pay.connector.charge.service;

import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import net.logstash.logback.argument.StructuredArguments;
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
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
import uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions;
import uk.gov.pay.connector.common.service.PatchRequestBuilder;
import uk.gov.pay.connector.events.EventQueue;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.StateTransitionQueue;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;
import uk.gov.pay.connector.wallets.WalletType;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Duration;
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
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.ChargeResponse.aChargeResponseBuilder;
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

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final CardTypeDao cardTypeDao;
    private final TokenDao tokenDao;
    private final GatewayAccountDao gatewayAccountDao;
    private final LinksConfig linksConfig;
    private final CaptureProcessConfig captureProcessConfig;
    private final PaymentProviders providers;

    private final StateTransitionQueue stateTransitionQueue;
    private final Boolean shouldEmitPaymentStateTransitionEvents;
    private EventQueue eventQueue;

    @Inject
    public ChargeService(TokenDao tokenDao, ChargeDao chargeDao, ChargeEventDao chargeEventDao,
                         CardTypeDao cardTypeDao, GatewayAccountDao gatewayAccountDao,
                         ConnectorConfiguration config, PaymentProviders providers,
                         StateTransitionQueue stateTransitionQueue, EventQueue eventQueue) {
        this.tokenDao = tokenDao;
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.cardTypeDao = cardTypeDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.linksConfig = config.getLinks();
        this.providers = providers;
        this.captureProcessConfig = config.getCaptureProcessConfig();
        this.stateTransitionQueue = stateTransitionQueue;
        this.shouldEmitPaymentStateTransitionEvents = config.getEmitPaymentStateTransitionEvents();
        this.eventQueue = eventQueue;
    }

    public Optional<ChargeResponse> create(ChargeCreateRequest chargeRequest, Long accountId, UriInfo uriInfo) {
        return createCharge(chargeRequest, accountId, uriInfo)
                .map(charge ->
                    populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, charge, false).build()
                );
    }

    @Transactional
    private Optional<ChargeEntity> createCharge(ChargeCreateRequest chargeRequest, Long accountId, UriInfo uriInfo) {
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
            transitionChargeState(chargeEntity, CREATED);
            chargeDao.merge(chargeEntity);
            return chargeEntity;
        });
    }

    private CardDetailsEntity createCardDetailsEntity(PrefilledCardHolderDetails prefilledCardHolderDetails) {
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity();
        prefilledCardHolderDetails.getCardHolderName().ifPresent(cardDetailsEntity::setCardHolderName);
        prefilledCardHolderDetails.getAddress().map(AddressEntity::new).ifPresent(cardDetailsEntity::setBillingAddress);
        return cardDetailsEntity;
    }

    @Transactional
    public Optional<ChargeResponse> findChargeForAccount(String chargeId, Long accountId, UriInfo uriInfo) {
        return chargeDao
                .findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, chargeEntity, false).build());
    }

    @Transactional
    public Optional<ChargeResponse> findChargeByGatewayTransactionId(String gatewayTransactionId, UriInfo uriInfo) {
        return chargeDao
                .findByGatewayTransactionId(gatewayTransactionId)
                .map(chargeEntity -> populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, chargeEntity, false).build());
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
                        transitionChargeState(chargeEntity, newChargeStatus);
                        return chargeEntity;
                    }
                    return null;
                });
    }

    public <T extends AbstractChargeResponseBuilder<T, R>, R> AbstractChargeResponseBuilder<T, R> populateResponseBuilderWith(AbstractChargeResponseBuilder<T, R> responseBuilder, UriInfo uriInfo, ChargeEntity chargeEntity, boolean buildForSearchResult) {
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

        if (needsNextUrl(chargeEntity, buildForSearchResult)) {
            TokenEntity token = createNewChargeEntityToken(chargeEntity);
            Map<String, Object> params = new HashMap<>();
            params.put("chargeTokenId", token.getToken());

            return builderOfResponse
                    .withLink("next_url", GET, nextUrl(token.getToken()))
                    .withLink("next_url_post", POST, nextUrl(), APPLICATION_FORM_URLENCODED, params);
        } else {
            return builderOfResponse;
        }
    }

    private boolean needsNextUrl(ChargeEntity chargeEntity, boolean buildForSearchResult) {
        ChargeStatus chargeStatus = ChargeStatus.fromString(chargeEntity.getStatus());
        return !buildForSearchResult && !chargeStatus.toExternal().isFinished() && !chargeStatus.equals(AWAITING_CAPTURE_REQUEST);
    }

    public ChargeEntity updateChargePostCardAuthorisation(String chargeExternalId,
                                                          ChargeStatus status,
                                                          Optional<String> transactionId,
                                                          Optional<Auth3dsDetailsEntity> auth3dsDetails,
                                                          Optional<String> sessionIdentifier,
                                                          AuthCardDetails authCardDetails) {
        return updateChargeAndEmitEventPostAuthorisation(chargeExternalId, status, authCardDetails, transactionId, auth3dsDetails, sessionIdentifier,
                Optional.empty(), Optional.empty());

    }

    public ChargeEntity updateChargePostWalletAuthorisation(String chargeExternalId,
                                                            ChargeStatus status,
                                                            Optional<String> transactionId,
                                                            Optional<String> sessionIdentifier,
                                                            AuthCardDetails authCardDetails,
                                                            WalletType walletType,
                                                            String emailAddress) {
        return updateChargeAndEmitEventPostAuthorisation(chargeExternalId, status, authCardDetails, transactionId, Optional.empty(), sessionIdentifier,
                Optional.ofNullable(walletType), Optional.ofNullable(emailAddress));
    }

    public ChargeEntity updateChargeAndEmitEventPostAuthorisation(String chargeExternalId,
                                                                  ChargeStatus status,
                                                                  AuthCardDetails authCardDetails,
                                                                  Optional<String> transactionId,
                                                                  Optional<Auth3dsDetailsEntity> auth3dsDetails,
                                                                  Optional<String> sessionIdentifier,
                                                                  Optional<WalletType> walletType,
                                                                  Optional<String> emailAddress) {
        updateChargePostAuthorisation(chargeExternalId, status, authCardDetails, transactionId,
                auth3dsDetails, sessionIdentifier, walletType, emailAddress);
        ChargeEntity chargeEntity = findChargeById(chargeExternalId);

        emitEvent(PaymentDetailsEntered.from(chargeEntity));

        return chargeEntity;
    }

    private void emitEvent(Event event) {
        try {
            eventQueue.emitEvent(event);
        } catch (QueueException e) {
            logger.error("Error emitting {} event: {}", event.getEventType(), e.getMessage());
            throw new WebApplicationException(format("Error emitting %s event: %s", event.getEventType(), e.getMessage()));
        }
    }

    // cannot be private: Guice requires @Transactional methods to be public
    @Transactional
    public ChargeEntity updateChargePostAuthorisation(String chargeExternalId,
                                                      ChargeStatus status,
                                                      AuthCardDetails authCardDetails,
                                                      Optional<String> transactionId,
                                                      Optional<Auth3dsDetailsEntity> auth3dsDetails,
                                                      Optional<String> sessionIdentifier,
                                                      Optional<WalletType> walletType,
                                                      Optional<String> emailAddress) {
        return chargeDao.findByExternalId(chargeExternalId).map(charge -> {
            setTransactionId(charge, transactionId);
            sessionIdentifier.ifPresent(charge::setProviderSessionId);
            auth3dsDetails.ifPresent(charge::set3dsDetails);
            walletType.ifPresent(charge::setWalletType);
            emailAddress.ifPresent(charge::setEmail);

            CardDetailsEntity detailsEntity = buildCardDetailsEntity(authCardDetails);
            charge.setCardDetails(detailsEntity);

            transitionChargeState(charge, status);

            logger.info("Stored confirmation details for charge - charge_external_id={}",
                    chargeExternalId);

            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
    }

    @Transactional
    public ChargeEntity updateChargePost3dsAuthorisation(String chargeExternalId, ChargeStatus status,
                                                         OperationType operationType,
                                                         Optional<String> transactionId) {
        return chargeDao.findByExternalId(chargeExternalId).map(charge -> {
            try {
                setTransactionId(charge, transactionId);
                transitionChargeState(charge, status);
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
                    if (nextStatus == CAPTURED) {
                        transitionChargeState(chargeEntity, CAPTURE_SUBMITTED);
                        transitionChargeState(chargeEntity, CAPTURED);
                    } else {
                        transitionChargeState(chargeEntity, nextStatus);
                    }
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

                // Used by Splunk saved search
//                logger.info("Card pre-operation - charge_external_id={}, charge_status={}, account_id={}, amount={}, operation_type={}, provider={}, provider_type={}, locking_status={}",
//                        chargeEntity.getExternalId(),
//                        fromString(chargeEntity.getStatus()),
//                        gatewayAccount.getId(),
//                        chargeEntity.getAmount(),
//                        operationType.getValue(),
//                        gatewayAccount.getGatewayName(),
//                        gatewayAccount.getType(),
//                        operationType.getLockingStatus());
                
//                logger.info("Card pre-operation - {}", 
//                        kv("charge_external_id", chargeEntity.getExternalId()),
//                        kv("charge_status", fromString(chargeEntity.getStatus())),
//                        kv("account_id", gatewayAccount.getId()),
//                        kv("amount", chargeEntity.getAmount()),
//                        kv("operation_type", operationType.getValue()),
//                        kv("provider", gatewayAccount.getGatewayName()),
//                        kv("provider_type", gatewayAccount.getType()),
//                        kv("locking_status", operationType.getLockingStatus())
//                );

//                logger.info("Card pre-operation - {}",
//                        Map.of("charge_external_id", chargeEntity.getExternalId(),
//                                "charge_status", fromString(chargeEntity.getStatus()),
//                                "account_id", gatewayAccount.getId(),
//                                "amount", chargeEntity.getAmount(),
//                                "operation_type", operationType.getValue(),
//                                "provider", gatewayAccount.getGatewayName(),
//                                "provider_type", gatewayAccount.getType(),
//                                "locking_status", operationType.getLockingStatus())
//                );
                
                logger.info("Card pre-operation {}", StructuredArguments.value("charge_external_id", chargeEntity.getExternalId()));

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

    public int getNumberOfChargesAwaitingCapture(Duration notAttemptedWithin) {
        return chargeDao.countChargesForImmediateCapture(notAttemptedWithin);
    }

    public ChargeEntity findChargeById(String chargeId) {
        return chargeDao.findByExternalId(chargeId)
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    public ChargeEntity transitionChargeState(ChargeEntity charge, ChargeStatus targetChargeState) {
        return transitionChargeState(charge, targetChargeState, null);
    }

    @Transactional
    public ChargeEntity transitionChargeState(
            ChargeEntity charge,
            ChargeStatus targetChargeState,
            ZonedDateTime gatewayEventTime
    ) {
        ChargeStatus fromChargeState = ChargeStatus.fromString(charge.getStatus());
        charge.setStatus(targetChargeState);
        ChargeEventEntity chargeEventEntity = chargeEventDao.persistChargeEventOf(charge, gatewayEventTime);

        PaymentGatewayStateTransitions.getInstance()
                .getEventForTransition(fromChargeState, targetChargeState)
                .ifPresent(eventType -> {
                    if (shouldEmitPaymentStateTransitionEvents) {
                        PaymentStateTransition transition = new PaymentStateTransition(chargeEventEntity.getId(), eventType);
                        stateTransitionQueue.offer(transition);
                        logger.info("Offered payment state transition to emitter queue [from={}] [to={}] [chargeEventId={}] [chargeId={}]", fromChargeState, targetChargeState, chargeEventEntity.getId(), charge.getExternalId());
                    }
                });
        return charge;
    }

    @Transactional
    public ChargeEntity transitionChargeState(String chargeId, ChargeStatus targetChargeState) {
        return chargeDao.findByExternalId(chargeId).map(chargeEntity ->
                transitionChargeState(chargeEntity, targetChargeState)
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
                transitionChargeState(charge, targetStatus);
            } catch (InvalidStateTransitionException e) {
                throw new IllegalStateRuntimeException(charge.getExternalId());
            }

            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));
    }

    @Transactional
    public ChargeEntity markDelayedCaptureChargeAsCaptureApproved(String externalId) {
        return chargeDao.findByExternalId(externalId).map(charge -> {
            switch (fromString(charge.getStatus())) {
                case AWAITING_CAPTURE_REQUEST:
                    try {
                        transitionChargeState(charge, CAPTURE_APPROVED);
                    } catch (InvalidStateTransitionException e) {
                        throw new ConflictRuntimeException(charge.getExternalId(),
                                "attempt to perform delayed capture on invalid charge state " + e.getMessage());
                    }

                    return charge;

                case CAPTURE_APPROVED:
                case CAPTURE_APPROVED_RETRY:
                case CAPTURE_READY:
                case CAPTURE_SUBMITTED:
                case CAPTURED:
                    return charge;

                default:
                    throw new ConflictRuntimeException(charge.getExternalId(),
                            format("attempt to perform delayed capture on charge not in %s state.", AWAITING_CAPTURE_REQUEST)
                    );
            }

        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));
    }

    public boolean isChargeRetriable(String externalId) {
        int numberOfChargeRetries = chargeDao.countCaptureRetriesForChargeExternalId(externalId);
        return numberOfChargeRetries <= captureProcessConfig.getMaximumRetries();
    }

    public boolean isChargeCaptureSuccess(String externalId) {
        ChargeEntity charge = findChargeById(externalId);
        ChargeStatus status = ChargeStatus.fromString(charge.getStatus());
        return status == CAPTURED || status == CAPTURE_SUBMITTED;
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
}
