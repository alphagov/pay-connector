package uk.gov.pay.connector.charge.service;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.AgreementIdAndSaveInstrumentMandatoryInputException;
import uk.gov.pay.connector.charge.exception.MotoPaymentNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.exception.ZeroAmountNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.exception.AgreementNotFoundException;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.PrefilledCardHolderDetails;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.model.domain.PersistedCard;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.Supplemental;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.charge.resource.ChargesApiResource;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.charge.util.RefundCalculator;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.InvalidForceStateTransitionException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
import uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions;
import uk.gov.pay.connector.common.model.domain.PrefilledAddress;
import uk.gov.pay.connector.common.service.PatchRequestBuilder;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.charge.Gateway3dsInfoObtained;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.events.model.charge.UserEmailCollected;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.service.payments.commons.model.SupportedLanguage;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.now;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.charge.model.ChargeResponse.aChargeResponseBuilder;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntity.TelephoneChargeEntityBuilder.aTelephoneChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntity.WebChargeEntityBuilder.aWebChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.PAYMENT_NOTIFICATION_CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.fromString;
import static uk.gov.pay.connector.common.model.domain.NumbersInStringsSanitizer.sanitize;

public class ChargeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChargeService.class);

    private static final List<ChargeStatus> CURRENT_STATUSES_ALLOWING_UPDATE_TO_NEW_STATUS = newArrayList(CREATED, ENTERING_CARD_DETAILS);

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final CardTypeDao cardTypeDao;
    private final TokenDao tokenDao;
    private final AgreementDao agreementDao;
    private final GatewayAccountDao gatewayAccountDao;
    private final LinksConfig linksConfig;
    private final CaptureProcessConfig captureProcessConfig;
    private final PaymentProviders providers;

    private final StateTransitionService stateTransitionService;
    private final LedgerService ledgerService;
    private final Boolean shouldEmitPaymentStateTransitionEvents;
    private final RefundService refundService;
    private final EventService eventService;
    private final GatewayAccountCredentialsService gatewayAccountCredentialsService;
    private final NorthAmericanRegionMapper northAmericanRegionMapper;
    private TaskQueueService taskQueueService;

    @Inject
    public ChargeService(TokenDao tokenDao,
                         ChargeDao chargeDao,
                         ChargeEventDao chargeEventDao,
                         CardTypeDao cardTypeDao,
                         AgreementDao agreementDao,
                         GatewayAccountDao gatewayAccountDao,
                         ConnectorConfiguration config,
                         PaymentProviders providers,
                         StateTransitionService stateTransitionService,
                         LedgerService ledgerService,
                         RefundService refundService,
                         EventService eventService,
                         GatewayAccountCredentialsService gatewayAccountCredentialsService,
                         NorthAmericanRegionMapper northAmericanRegionMapper,
                         TaskQueueService taskQueueService) {
        this.tokenDao = tokenDao;
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.cardTypeDao = cardTypeDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.agreementDao = agreementDao;
        this.linksConfig = config.getLinks();
        this.providers = providers;
        this.captureProcessConfig = config.getCaptureProcessConfig();
        this.stateTransitionService = stateTransitionService;
        this.shouldEmitPaymentStateTransitionEvents = config.getEmitPaymentStateTransitionEvents();
        this.ledgerService = ledgerService;
        this.refundService = refundService;
        this.eventService = eventService;
        this.gatewayAccountCredentialsService = gatewayAccountCredentialsService;
        this.northAmericanRegionMapper = northAmericanRegionMapper;
        this.taskQueueService = taskQueueService;
    }

    @Transactional
    public Optional<ChargeResponse> findCharge(Long gatewayAccountId, TelephoneChargeCreateRequest telephoneChargeRequest) {
        return chargeDao.findByGatewayTransactionIdAndAccount(gatewayAccountId, telephoneChargeRequest.getProviderId())
                .map(charge -> populateResponseBuilderWith(aChargeResponseBuilder(), charge).build());
    }

    public ChargeResponse createFromTelephonePaymentNotification(TelephoneChargeCreateRequest telephoneChargeCreateRequest, GatewayAccountEntity gatewayAccount) {
        ChargeEntity charge = createTelephoneCharge(telephoneChargeCreateRequest, gatewayAccount);
        return populateResponseBuilderWith(aChargeResponseBuilder(), charge).build();
    }

    @Transactional
    private ChargeEntity createTelephoneCharge(TelephoneChargeCreateRequest telephoneChargeRequest, GatewayAccountEntity gatewayAccount) {
        checkIfZeroAmountAllowed(telephoneChargeRequest.getAmount(), gatewayAccount);

        CardDetailsEntity cardDetails = new CardDetailsEntity(
                LastDigitsCardNumber.ofNullable(telephoneChargeRequest.getLastFourDigits().orElse(null)),
                FirstDigitsCardNumber.ofNullable(telephoneChargeRequest.getFirstSixDigits().orElse(null)),
                telephoneChargeRequest.getNameOnCard().orElse(null),
                telephoneChargeRequest.getCardExpiry().orElse(null),
                telephoneChargeRequest.getCardType().orElse(null),
                null
        );

        GatewayAccountCredentialsEntity gatewayAccountCredential
                = gatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount);

        ChargeEntity chargeEntity = aTelephoneChargeEntity()
                .withAmount(telephoneChargeRequest.getAmount())
                .withDescription(telephoneChargeRequest.getDescription())
                .withReference(ServicePaymentReference.of(telephoneChargeRequest.getReference()))
                .withGatewayAccount(gatewayAccount)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredential)
                .withPaymentProvider(gatewayAccountCredential.getPaymentProvider())
                .withEmail(telephoneChargeRequest.getEmailAddress().orElse(null))
                .withExternalMetadata(storeExtraFieldsInMetaData(telephoneChargeRequest))
                .withGatewayTransactionId(telephoneChargeRequest.getProviderId())
                .withCardDetails(cardDetails)
                .withServiceId(gatewayAccount.getServiceId())
                .build();

        chargeDao.persist(chargeEntity);
        transitionChargeState(chargeEntity, PAYMENT_NOTIFICATION_CREATED);
        transitionChargeState(chargeEntity, internalChargeStatus(telephoneChargeRequest.getPaymentOutcome().getCode().orElse(null)));
        chargeDao.merge(chargeEntity);
        return chargeEntity;
    }

    private ChargeStatus internalChargeStatus(String code) {
        if (code == null) {
            return CAPTURE_SUBMITTED;
        } else if ("P0010".equals(code)) {
            return AUTHORISATION_REJECTED;
        } else {
            return AUTHORISATION_ERROR;
        }
    }

    public Optional<ChargeResponse> create(ChargeCreateRequest chargeRequest, Long accountId, UriInfo uriInfo) {
        return createCharge(chargeRequest, accountId, uriInfo)
                .map(charge ->
                        populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, charge).build()
                );
    }

    @Transactional
    private Optional<ChargeEntity> createCharge(ChargeCreateRequest chargeRequest, Long accountId, UriInfo uriInfo) {
        return gatewayAccountDao.findById(accountId).map(gatewayAccount -> {

            checkIfZeroAmountAllowed(chargeRequest.getAmount(), gatewayAccount);
            checkIfMotoPaymentsAllowed(chargeRequest.isMoto(), gatewayAccount);
            
            checkAgreementIdAndSaveInstrumentBothPresent(chargeRequest.getAgreementId(), chargeRequest.getSavePaymentInstrumentToAgreement());

            checkForUnknownAgreementId(chargeRequest.getAgreementId());

            if (gatewayAccount.isLive() && !chargeRequest.getReturnUrl().startsWith("https://")) {
                LOGGER.info(String.format("Gateway account %d is LIVE, but is configured to use a non-https return_url", accountId));
            }

            SupportedLanguage language = chargeRequest.getLanguage() != null
                    ? chargeRequest.getLanguage()
                    : SupportedLanguage.ENGLISH;

            GatewayAccountCredentialsEntity gatewayAccountCredential;
            if (chargeRequest.getPaymentProvider() != null) {
                gatewayAccountCredential = gatewayAccountCredentialsService.getUsableCredentialsForProvider(
                        gatewayAccount, chargeRequest.getPaymentProvider());
            } else {
                gatewayAccountCredential = gatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount);
            }

            ChargeEntity chargeEntity = aWebChargeEntity()
                    .withAmount(chargeRequest.getAmount())
                    .withReturnUrl(chargeRequest.getReturnUrl())
                    .withDescription(chargeRequest.getDescription())
                    .withReference(ServicePaymentReference.of(chargeRequest.getReference()))
                    .withGatewayAccount(gatewayAccount)
                    .withGatewayAccountCredentialsEntity(gatewayAccountCredential)
                    .withPaymentProvider(gatewayAccountCredential.getPaymentProvider())
                    .withEmail(isBlank(chargeRequest.getEmail()) ? null : chargeRequest.getEmail())
                    .withLanguage(language)
                    .withDelayedCapture(chargeRequest.isDelayedCapture())
                    .withExternalMetadata(chargeRequest.getExternalMetadata().orElse(null))
                    .withSource(chargeRequest.getSource())
                    .withMoto(chargeRequest.isMoto())
                    .withServiceId(gatewayAccount.getServiceId())
                    .withSavePaymentInstrumentToAgreement(chargeRequest.getSavePaymentInstrumentToAgreement())
                    .withAgreementId(chargeRequest.getAgreementId())
                    .build();

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
        prefilledCardHolderDetails.getAddress().map(PrefilledAddress::toAddress).map(AddressEntity::new).ifPresent(cardDetailsEntity::setBillingAddress);
        return cardDetailsEntity;
    }

    @Transactional
    public Optional<ChargeResponse> findChargeForAccount(String chargeId, Long accountId, UriInfo uriInfo) {
        return chargeDao
                .findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, chargeEntity).build());
    }

    @Transactional
    public Optional<ChargeResponse> findChargeByGatewayTransactionId(String gatewayTransactionId, UriInfo uriInfo) {
        return chargeDao
                .findByGatewayTransactionId(gatewayTransactionId)
                .map(chargeEntity -> populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, chargeEntity).build());
    }

    @Transactional
    public Optional<ChargeEntity> updateChargeParityStatus(String externalId, ParityCheckStatus parityCheckStatus) {
        return chargeDao.findByExternalId(externalId)
                .map(chargeEntity -> {
                    chargeEntity.updateParityCheck(parityCheckStatus);
                    return Optional.of(chargeEntity);
                })
                .orElseGet(Optional::empty);
    }

    public Optional<Charge> findCharge(String chargeExternalId) {
        Optional<ChargeEntity> maybeChargeEntity = chargeDao.findByExternalId(chargeExternalId);

        if (maybeChargeEntity.isPresent()) {
            return maybeChargeEntity.map(Charge::from);
        } else {
            return ledgerService.getTransaction(chargeExternalId).map(Charge::from);
        }
    }

    public Optional<Charge> findCharge(String chargeExternalId, Long gatewayAccountId) {
        Optional<ChargeEntity> maybeChargeEntity = chargeDao.findByExternalIdAndGatewayAccount(chargeExternalId, gatewayAccountId);

        if (maybeChargeEntity.isPresent()) {
            return maybeChargeEntity.map(Charge::from);
        } else {
            return ledgerService.getTransactionForGatewayAccount(chargeExternalId, gatewayAccountId).map(Charge::from);
        }
    }

    public Optional<Charge> findByProviderAndTransactionIdFromDbOrLedger(String paymentGatewayName, String gatewayTransactionId) {
        return Optional.ofNullable(chargeDao.findByProviderAndTransactionId(paymentGatewayName, gatewayTransactionId)
                .map(Charge::from)
                .orElseGet(() -> findChargeFromLedger(paymentGatewayName, gatewayTransactionId).orElse(null)));
    }

    private Optional<Charge> findChargeFromLedger(String paymentGatewayName, String gatewayTransactionId) {
        return ledgerService.getTransactionForProviderAndGatewayTransactionId(paymentGatewayName, gatewayTransactionId).map(Charge::from);
    }

    @Transactional
    public Optional<ChargeEntity> updateCharge(String chargeId, PatchRequestBuilder.PatchRequest chargePatchRequest) {
        return chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> {
                    if (chargePatchRequest.getPath().equals(ChargesApiResource.EMAIL_KEY)) {
                        chargeEntity.setEmail(chargePatchRequest.getValue());
                        eventService.emitAndRecordEvent(UserEmailCollected.from(chargeEntity, now(UTC)));
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

    private ChargeResponse.ChargeResponseBuilder populateResponseBuilderWith(
            AbstractChargeResponseBuilder<ChargeResponse.ChargeResponseBuilder, ChargeResponse> responseBuilder,
            ChargeEntity chargeEntity) {

        PersistedCard persistedCard = null;
        if (chargeEntity.getCardDetails() != null) {
            persistedCard = chargeEntity.getCardDetails().toCard();
        }

        ChargeResponse.ChargeResponseBuilder builderOfResponse = responseBuilder
                .withAmount(chargeEntity.getAmount())
                .withReference(chargeEntity.getReference())
                .withDescription(chargeEntity.getDescription())
                .withProviderId(chargeEntity.getGatewayTransactionId())
                .withCardDetails(persistedCard)
                .withEmail(chargeEntity.getEmail())
                .withChargeId(chargeEntity.getExternalId());

        chargeEntity.getExternalMetadata().ifPresent(externalMetadata -> {

            final PaymentOutcome paymentOutcome = new PaymentOutcome(
                    externalMetadata.getMetadata().get("status").toString()
            );

            ExternalTransactionState state;

            if (externalMetadata.getMetadata().get("status").toString().equals("success")) {
                state = new ExternalTransactionState(
                        externalMetadata.getMetadata().get("status").toString(),
                        true
                );
            } else {
                String message = Stream.of(ExternalChargeState.values())
                        .filter(chargeState -> chargeState.getCode() != null)
                        .collect(Collectors.toMap(ExternalChargeState::getCode, ExternalChargeState::getMessage))
                        .get(externalMetadata.getMetadata().get("code").toString());

                state = new ExternalTransactionState(
                        externalMetadata.getMetadata().get("status").toString(),
                        true,
                        externalMetadata.getMetadata().get("code").toString(),
                        message
                );
                paymentOutcome.setCode(externalMetadata.getMetadata().get("code").toString());
            }

            if (externalMetadata.getMetadata().get("error_code") != null || externalMetadata.getMetadata().get("error_message") != null) {
                paymentOutcome.setSupplemental(new Supplemental(
                        (String) externalMetadata.getMetadata().get("error_code"),
                        (String) externalMetadata.getMetadata().get("error_message")
                ));
            }

            if (externalMetadata.getMetadata().get("authorised_date") != null) {
                builderOfResponse.withAuthorisedDate(ZonedDateTime.parse(((String) externalMetadata.getMetadata().get("authorised_date"))).toInstant());
            }

            if (externalMetadata.getMetadata().get("created_date") != null) {
                builderOfResponse.withCreatedDate(ZonedDateTime.parse(((String) externalMetadata.getMetadata().get("created_date"))).toInstant());
            }

            builderOfResponse
                    .withProcessorId((String) externalMetadata.getMetadata().get("processor_id"))
                    .withAuthCode((String) externalMetadata.getMetadata().get("auth_code"))
                    .withTelephoneNumber((String) externalMetadata.getMetadata().get("telephone_number"))
                    .withState(state)
                    .withPaymentOutcome(paymentOutcome);
        });

        return builderOfResponse;
    }

    public <T extends AbstractChargeResponseBuilder<T, R>, R> AbstractChargeResponseBuilder<T, R> populateResponseBuilderWith(
            AbstractChargeResponseBuilder<T, R> responseBuilder,
            UriInfo uriInfo,
            ChargeEntity chargeEntity) {
        String chargeId = chargeEntity.getExternalId();
        PersistedCard persistedCard = null;
        if (chargeEntity.getCardDetails() != null) {
            persistedCard = chargeEntity.getCardDetails().toCard();
            persistedCard.setCardBrand(findCardBrandLabel(chargeEntity.getCardDetails().getCardBrand()).orElse(""));
        }

        ChargeResponse.Auth3dsData auth3dsData = null;
        ChargeResponse.AuthorisationSummary authorisationSummary = null;
        if (chargeEntity.get3dsRequiredDetails() != null) {
            auth3dsData = new ChargeResponse.Auth3dsData();
            auth3dsData.setPaRequest(chargeEntity.get3dsRequiredDetails().getPaRequest());
            auth3dsData.setIssuerUrl(chargeEntity.get3dsRequiredDetails().getIssuerUrl());

            authorisationSummary = new ChargeResponse.AuthorisationSummary();
            ChargeResponse.AuthorisationSummary.ThreeDSecure threeDSecure = new ChargeResponse.AuthorisationSummary.ThreeDSecure();
            threeDSecure.setRequired(true);
            threeDSecure.setVersion(chargeEntity.get3dsRequiredDetails().getThreeDsVersion());
            authorisationSummary.setThreeDSecure(threeDSecure);
        }
        ExternalChargeState externalChargeState = ChargeStatus.fromString(chargeEntity.getStatus()).toExternal();

        T builderOfResponse = responseBuilder
                .withChargeId(chargeId)
                .withAmount(chargeEntity.getAmount())
                .withReference(chargeEntity.getReference())
                .withDescription(chargeEntity.getDescription())
                .withState(new ExternalTransactionState(externalChargeState.getStatus(), externalChargeState.isFinished(), externalChargeState.getCode(), externalChargeState.getMessage()))
                .withGatewayTransactionId(chargeEntity.getGatewayTransactionId())
                .withProviderName(chargeEntity.getPaymentProvider())
                .withCreatedDate(chargeEntity.getCreatedDate())
                .withReturnUrl(chargeEntity.getReturnUrl())
                .withEmail(chargeEntity.getEmail())
                .withLanguage(chargeEntity.getLanguage())
                .withDelayedCapture(chargeEntity.isDelayedCapture())
                .withRefunds(buildRefundSummary(chargeEntity))
                .withSettlement(buildSettlementSummary(chargeEntity))
                .withCardDetails(persistedCard)
                .withAuth3dsData(auth3dsData)
                .withAuthorisationSummary(authorisationSummary)
                .withLink("self", GET, selfUriFor(uriInfo, chargeEntity.getGatewayAccount().getId(), chargeId))
                .withLink("refunds", GET, refundsUriFor(uriInfo, chargeEntity.getGatewayAccount().getId(), chargeEntity.getExternalId()))
                .withWalletType(chargeEntity.getWalletType())
                .withMoto(chargeEntity.isMoto())
                .withAgreementId(chargeEntity.getAgreementId());

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

        if (needsNextUrl(chargeEntity)) {
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

    private boolean needsNextUrl(ChargeEntity chargeEntity) {
        ChargeStatus chargeStatus = ChargeStatus.fromString(chargeEntity.getStatus());
        return !chargeStatus.toExternal().isFinished() && !chargeStatus.equals(AWAITING_CAPTURE_REQUEST);
    }

    public ChargeEntity updateChargePostCardAuthorisation(String chargeExternalId,
                                                          ChargeStatus status,
                                                          String transactionId,
                                                          Auth3dsRequiredEntity auth3dsRequiredDetails,
                                                          ProviderSessionIdentifier sessionIdentifier,
                                                          AuthCardDetails authCardDetails) {
        return updateChargeAndEmitEventPostAuthorisation(chargeExternalId, status, authCardDetails, transactionId, auth3dsRequiredDetails, sessionIdentifier,
                null, null);

    }

    public ChargeEntity updateChargePostWalletAuthorisation(String chargeExternalId,
                                                            ChargeStatus status,
                                                            String transactionId,
                                                            ProviderSessionIdentifier sessionIdentifier,
                                                            AuthCardDetails authCardDetails,
                                                            WalletType walletType,
                                                            String emailAddress,
                                                            Optional<Auth3dsRequiredEntity> auth3dsRequiredDetails) {
        return updateChargeAndEmitEventPostAuthorisation(chargeExternalId, status, authCardDetails, transactionId, auth3dsRequiredDetails.orElse(null), sessionIdentifier,
                walletType, emailAddress);
    }

    ChargeEntity updateChargeAndEmitEventPostAuthorisation(String chargeExternalId,
                                                           ChargeStatus status,
                                                           AuthCardDetails authCardDetails,
                                                           String transactionId,
                                                           Auth3dsRequiredEntity auth3dsRequiredDetails,
                                                           ProviderSessionIdentifier sessionIdentifier,
                                                           WalletType walletType,
                                                           String emailAddress) {
        updateChargePostAuthorisation(chargeExternalId, status, authCardDetails, transactionId,
                auth3dsRequiredDetails, sessionIdentifier, walletType, emailAddress);
        ChargeEntity chargeEntity = findChargeByExternalId(chargeExternalId);

        eventService.emitAndRecordEvent(PaymentDetailsEntered.from(chargeEntity));

        return chargeEntity;
    }

    // cannot be private: Guice requires @Transactional methods to be public
    @Transactional
    public ChargeEntity updateChargePostAuthorisation(String chargeExternalId,
                                                      ChargeStatus status,
                                                      AuthCardDetails authCardDetails,
                                                      String transactionId,
                                                      Auth3dsRequiredEntity auth3dsRequiredDetails,
                                                      ProviderSessionIdentifier sessionIdentifier,
                                                      WalletType walletType,
                                                      String emailAddress) {
        return chargeDao.findByExternalId(chargeExternalId).map(charge -> {
            setTransactionId(charge, transactionId);
            Optional.ofNullable(sessionIdentifier).map(ProviderSessionIdentifier::toString).ifPresent(charge::setProviderSessionId);
            Optional.ofNullable(auth3dsRequiredDetails).ifPresent(charge::set3dsRequiredDetails);
            Optional.ofNullable(walletType).ifPresent(charge::setWalletType);
            Optional.ofNullable(emailAddress).ifPresent(charge::setEmail);

            CardDetailsEntity detailsEntity = buildCardDetailsEntity(authCardDetails);
            charge.setCardDetails(detailsEntity);

            transitionChargeState(charge, status);

            LOGGER.info("Stored confirmation details for charge - charge_external_id={}",
                    chargeExternalId);

            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
    }

    @Transactional
    public ChargeEntity updateChargePost3dsAuthorisation(String chargeExternalId, ChargeStatus status,
                                                         OperationType operationType,
                                                         String transactionId,
                                                         Auth3dsRequiredEntity auth3dsRequiredDetails,
                                                         ProviderSessionIdentifier sessionIdentifier) {
        return chargeDao.findByExternalId(chargeExternalId).map(charge -> {
            try {
                setTransactionId(charge, transactionId);
                transitionChargeState(charge, status);
                Optional.ofNullable(auth3dsRequiredDetails).ifPresent(charge::set3dsRequiredDetails);
                Optional.ofNullable(sessionIdentifier).map(ProviderSessionIdentifier::toString).ifPresent(charge::setProviderSessionId);
            } catch (InvalidStateTransitionException e) {
                if (chargeIsInLockedStatus(operationType, charge)) {
                    throw new OperationAlreadyInProgressRuntimeException(operationType.getValue(), charge.getExternalId());
                }
                throw new IllegalStateRuntimeException(charge.getExternalId());
            }

            if (auth3dsRequiredDetails != null && isNotBlank(auth3dsRequiredDetails.getThreeDsVersion())) {
                eventService.emitAndRecordEvent(Gateway3dsInfoObtained.from(charge, ZonedDateTime.now()));
            }

            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
    }

    public ChargeEntity updateChargePostCapture(ChargeEntity chargeEntity, ChargeStatus nextStatus) {
        if (nextStatus == CAPTURED) {
            transitionChargeState(chargeEntity, CAPTURE_SUBMITTED);
            transitionChargeState(chargeEntity, CAPTURED);
        } else {
            transitionChargeState(chargeEntity, nextStatus);
        }
        return chargeEntity;
    }

    private void setTransactionId(ChargeEntity chargeEntity, String transactionId) {
        if (transactionId != null && !transactionId.isBlank()) {
            chargeEntity.setGatewayTransactionId(transactionId);
        }
    }

    @Transactional
    public ChargeEntity lockChargeForProcessing(String chargeId, OperationType operationType) {
        return chargeDao.findByExternalId(chargeId).map(chargeEntity -> {
            try {

                GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();

                // Used by Splunk saved search
                LOGGER.info("Card pre-operation - charge_external_id={}, charge_status={}, account_id={}, amount={}, operation_type={}, provider={}, provider_type={}, locking_status={}",
                        chargeEntity.getExternalId(),
                        fromString(chargeEntity.getStatus()),
                        gatewayAccount.getId(),
                        chargeEntity.getAmount(),
                        operationType.getValue(),
                        chargeEntity.getPaymentProvider(),
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

    public int getNumberOfChargesAwaitingCapture(Duration notAttemptedWithin) {
        return chargeDao.countChargesForImmediateCapture(notAttemptedWithin);
    }

    public ChargeEntity findChargeByExternalId(String chargeId) {
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

        if (shouldEmitPaymentStateTransitionEvents) {
            stateTransitionService.offerPaymentStateTransition(charge.getExternalId(), fromChargeState, targetChargeState, chargeEventEntity);
        }
        
        taskQueueService.offerTasksOnStateTransition(charge);

        return charge;
    }

    @Transactional
    public ChargeEntity transitionChargeState(String chargeExternalId, ChargeStatus targetChargeState) {
        return chargeDao.findByExternalId(chargeExternalId).map(chargeEntity ->
                transitionChargeState(chargeEntity, targetChargeState)
        ).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
    }

    @Transactional
    public ChargeEntity forceTransitionChargeState(String chargeExternalId, ChargeStatus targetChargeState) {
        return chargeDao.findByExternalId(chargeExternalId).map(chargeEntity ->
                forceTransitionChargeState(chargeEntity, targetChargeState, null))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
    }

    @Transactional
    public ChargeEntity forceTransitionChargeState(ChargeEntity charge, ChargeStatus targetChargeState, ZonedDateTime gatewayEventDate) {
        ChargeStatus fromChargeState = ChargeStatus.fromString(charge.getStatus());

        return PaymentGatewayStateTransitions.getEventForForceUpdate(targetChargeState).map(eventClass -> {
            charge.setStatusIgnoringValidTransitions(targetChargeState);
            ChargeEventEntity chargeEventEntity = chargeEventDao.persistChargeEventOf(charge, gatewayEventDate);

            if (shouldEmitPaymentStateTransitionEvents) {
                stateTransitionService.offerPaymentStateTransition(
                        charge.getExternalId(), fromChargeState, targetChargeState, chargeEventEntity,
                        eventClass);
            }
            
            taskQueueService.offerTasksOnStateTransition(charge);

            return charge;
        }).orElseThrow(() -> new InvalidForceStateTransitionException(fromChargeState, targetChargeState));
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
        ChargeEntity charge = findChargeByExternalId(externalId);
        ChargeStatus status = ChargeStatus.fromString(charge.getStatus());
        return status == CAPTURED || status == CAPTURE_SUBMITTED;
    }

    public int count3dsRequiredEvents(String externalId) {
        return chargeDao.count3dsRequiredEventsForChargeExternalId(externalId);
    }

    private CardDetailsEntity buildCardDetailsEntity(AuthCardDetails authCardDetails) {
        CardDetailsEntity detailsEntity = new CardDetailsEntity();
        detailsEntity.setCardBrand(sanitize(authCardDetails.getCardBrand()));
        detailsEntity.setCardHolderName(sanitize(authCardDetails.getCardHolder()));
        detailsEntity.setExpiryDate(authCardDetails.getEndDate());
        if (hasFullCardNumber(authCardDetails)) { // Apple Pay etc. don’t give us a full card number, just the last four digits here
            detailsEntity.setFirstDigitsCardNumber(FirstDigitsCardNumber.of(StringUtils.left(authCardDetails.getCardNo(), 6)));
        }

        if (hasLastFourCharactersCardNumber(authCardDetails)) {
            detailsEntity.setLastDigitsCardNumber(LastDigitsCardNumber.of(StringUtils.right(authCardDetails.getCardNo(), 4)));
        }

        authCardDetails.getAddress().ifPresent(address -> {
            var addressEntity = new AddressEntity(address);
            northAmericanRegionMapper.getNorthAmericanRegionForCountry(address)
                    .map(NorthAmericaRegion::getAbbreviation)
                    .ifPresent(stateOrProvinceAbbreviation -> {
                        addressEntity.setStateOrProvince(stateOrProvinceAbbreviation);
                    });
            detailsEntity.setBillingAddress(addressEntity);
        });

        detailsEntity.setCardType(PayersCardType.toCardType(authCardDetails.getPayersCardType()));

        return detailsEntity;
    }

    private boolean hasFullCardNumber(AuthCardDetails authCardDetails) {
        return authCardDetails.getCardNo().length() > 6;
    }

    private boolean hasLastFourCharactersCardNumber(AuthCardDetails authCardDetails) {
        return authCardDetails.getCardNo().length() >= 4;
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

    private ChargeResponse.RefundSummary buildRefundSummary(ChargeEntity chargeEntity) {
        ChargeResponse.RefundSummary refund = new ChargeResponse.RefundSummary();
        Charge charge = Charge.from(chargeEntity);
        List<Refund> refundList = refundService.findRefunds(charge);
        refund.setStatus(providers.byName(chargeEntity.getPaymentGatewayName()).getExternalChargeRefundAvailability(charge, refundList).getStatus());
        refund.setAmountSubmitted(RefundCalculator.getRefundedAmount(refundList));
        refund.setAmountAvailable(RefundCalculator.getTotalAmountAvailableToBeRefunded(charge, refundList));
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

    private ExternalMetadata storeExtraFieldsInMetaData(TelephoneChargeCreateRequest telephoneChargeRequest) {
        HashMap<String, Object> telephoneJSON = new HashMap<>();
        String processorId = telephoneChargeRequest.getProcessorId();
        telephoneJSON.put("processor_id", checkAndGetTruncatedValue(processorId, "processor_id", processorId));
        telephoneJSON.put("status", telephoneChargeRequest.getPaymentOutcome().getStatus());
        telephoneChargeRequest.getCreatedDate().ifPresent(createdDate -> telephoneJSON.put("created_date", createdDate));
        telephoneChargeRequest.getAuthorisedDate().ifPresent(authorisedDate -> telephoneJSON.put("authorised_date", authorisedDate));
        telephoneChargeRequest.getAuthCode().ifPresent(authCode -> telephoneJSON.put("auth_code", checkAndGetTruncatedValue(processorId, "auth_code", authCode)));
        telephoneChargeRequest.getTelephoneNumber().ifPresent(telephoneNumber -> telephoneJSON.put("telephone_number", checkAndGetTruncatedValue(processorId, "telephone_number", telephoneNumber)));
        telephoneChargeRequest.getPaymentOutcome().getCode().ifPresent(code -> telephoneJSON.put("code", code));
        telephoneChargeRequest.getPaymentOutcome().getSupplemental().ifPresent(
                supplemental -> {
                    supplemental.getErrorCode().ifPresent(errorCode -> telephoneJSON.put("error_code", checkAndGetTruncatedValue(processorId, "error_code", errorCode)));
                    supplemental.getErrorMessage().ifPresent(errorMessage -> telephoneJSON.put("error_message", checkAndGetTruncatedValue(processorId, "error_message", errorMessage)));
                }
        );

        return new ExternalMetadata(telephoneJSON);
    }

    private String checkAndGetTruncatedValue(String processorId, String field, String value) {
        if (value.length() > 50) {
            LOGGER.info("Telephone payment {} - {} field is longer than 50 characters and has been truncated and stored. Actual value is {}", processorId, field, value);
            return value.substring(0, 50);
        }
        return value;
    }

    private void checkIfZeroAmountAllowed(Long amount, GatewayAccountEntity gatewayAccount) {
        if (amount == 0L && !gatewayAccount.isAllowZeroAmount()) {
            throw new ZeroAmountNotAllowedForGatewayAccountException(gatewayAccount.getId());
        }
    }

    private void checkIfMotoPaymentsAllowed(boolean moto, GatewayAccountEntity gatewayAccount) {
        if (moto && !gatewayAccount.isAllowMoto()) {
            throw new MotoPaymentNotAllowedForGatewayAccountException(gatewayAccount.getId());
        }
    }

    private void checkAgreementIdAndSaveInstrumentBothPresent(String agreementId, boolean savePaymentInstrumentToAgreement) {
        if (agreementId != null && !savePaymentInstrumentToAgreement) {
            throw new AgreementIdAndSaveInstrumentMandatoryInputException("If [agreement_id] is present, [save_payment_instrument_to_agreement] must be true");
        } else if (savePaymentInstrumentToAgreement && agreementId == null) {
            throw new AgreementIdAndSaveInstrumentMandatoryInputException("If [save_payment_instrument_to_agreement] is true, [agreement_id] must be specified");
        }
    }

    private void checkForUnknownAgreementId(String agreementId) {
        if (agreementId != null) {
            agreementDao.findByExternalId(agreementId)
                    .orElseThrow(() -> new AgreementNotFoundException(format("Agreement with ID [%s] not found.", agreementId)));
        }
    }
}
