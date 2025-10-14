package uk.gov.pay.connector.charge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.persist.Transactional;
import jakarta.inject.Inject;
import jakarta.persistence.RollbackException;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.exception.RecurringCardPaymentsNotAllowedException;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeException;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.exception.GatewayAccountDisabledException;
import uk.gov.pay.connector.charge.exception.IdempotencyKeyUsedException;
import uk.gov.pay.connector.charge.exception.IncorrectAuthorisationModeForSavePaymentToAgreementException;
import uk.gov.pay.connector.charge.exception.MissingMandatoryAttributeException;
import uk.gov.pay.connector.charge.exception.UnexpectedAttributeException;
import uk.gov.pay.connector.charge.exception.ZeroAmountNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.exception.motoapi.AuthorisationApiNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.FrontendChargeResponse;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.PrefilledCardHolderDetails;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.model.domain.PersistedCard;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.Supplemental;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.charge.resource.ChargesApiResource;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.charge.util.RefundCalculator;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.cardid.service.CardidService;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.InvalidForceStateTransitionException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions;
import uk.gov.pay.connector.common.model.domain.PrefilledAddress;
import uk.gov.pay.connector.common.service.PatchRequestBuilder;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.eventdetails.charge.RefundAvailabilityUpdatedEventDetails;
import uk.gov.pay.connector.events.model.agreement.AgreementInactivated;
import uk.gov.pay.connector.events.model.charge.Gateway3dsInfoObtained;
import uk.gov.pay.connector.events.model.charge.GatewayDoesNotRequire3dsAuthorisation;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsSubmittedByAPI;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsTakenFromPaymentInstrument;
import uk.gov.pay.connector.events.model.charge.RefundAvailabilityUpdated;
import uk.gov.pay.connector.events.model.charge.UserEmailCollected;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.idempotency.model.IdempotencyEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.service.payments.commons.model.AgreementPaymentType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.Source;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.POST;
import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit.LUHN_CHECK_DIGIT;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static uk.gov.pay.connector.charge.model.ChargeResponse.aChargeResponseBuilder;
import static uk.gov.pay.connector.charge.model.FrontendChargeResponse.aFrontendChargeResponse;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntity.TelephoneChargeEntityBuilder.aTelephoneChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntity.WebChargeEntityBuilder.aWebChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_USER_NOT_PRESENT_QUEUED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.PAYMENT_NOTIFICATION_CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.fromString;
import static uk.gov.pay.connector.charge.model.domain.Exemption3dsType.CORPORATE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_NOT_REQUESTED;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.PAYMENT_CONFIRMED;
import static uk.gov.service.payments.commons.model.AuthorisationMode.AGREEMENT;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.AGREEMENT_NOT_ACTIVE;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.AGREEMENT_NOT_FOUND;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.AMOUNT_BELOW_MINIMUM;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.GENERIC;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.MOTO_NOT_ALLOWED;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.NON_HTTPS_RETURN_URL_NOT_ALLOWED_FOR_A_LIVE_ACCOUNT;
import static uk.gov.service.payments.commons.model.Source.CARD_AGENT_INITIATED_MOTO;
import static uk.gov.service.payments.commons.model.Source.CARD_EXTERNAL_TELEPHONE;
import static uk.gov.service.payments.commons.model.Source.CARD_PAYMENT_LINK;

public class ChargeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChargeService.class);
    private static final List<ChargeStatus> CURRENT_STATUSES_ALLOWING_UPDATE_TO_NEW_STATUS = newArrayList(CREATED, ENTERING_CARD_DETAILS);
    public static final int MINIMUM_STRIPE_PAYMENT_AMOUNT = 30;

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
    private final AuthCardDetailsToCardDetailsEntityConverter authCardDetailsToCardDetailsEntityConverter;
    private final PaymentInstrumentService paymentInstrumentService;
    private final TaskQueueService taskQueueService;
    private final IdempotencyDao idempotencyDao;
    private final ExternalTransactionStateFactory externalTransactionStateFactory;
    private final ObjectMapper objectMapper;

    private final Worldpay3dsFlexJwtService worldpay3dsFlexJwtService;

    private final Boolean rejectPaymentLinkPaymentsWithCardNumberInReference;
    private final CardidService cardidService;
    private final InstantSource instantSource;

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
                         PaymentInstrumentService paymentInstrumentService,
                         GatewayAccountCredentialsService gatewayAccountCredentialsService,
                         AuthCardDetailsToCardDetailsEntityConverter authCardDetailsToCardDetailsEntityConverter,
                         TaskQueueService taskQueueService,
                         Worldpay3dsFlexJwtService worldpay3dsFlexJwtService,
                         IdempotencyDao idempotencyDao,
                         ExternalTransactionStateFactory externalTransactionStateFactory,
                         ObjectMapper objectMapper,
                         CardidService cardidService,
                         InstantSource instantSource) {
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
        this.paymentInstrumentService = paymentInstrumentService;
        this.gatewayAccountCredentialsService = gatewayAccountCredentialsService;
        this.authCardDetailsToCardDetailsEntityConverter = authCardDetailsToCardDetailsEntityConverter;
        this.taskQueueService = taskQueueService;
        this.idempotencyDao = idempotencyDao;
        this.externalTransactionStateFactory = externalTransactionStateFactory;
        this.objectMapper = objectMapper;
        this.worldpay3dsFlexJwtService = worldpay3dsFlexJwtService;
        this.rejectPaymentLinkPaymentsWithCardNumberInReference = config.getRejectPaymentLinkPaymentsWithCardNumberInReference();
        this.cardidService = cardidService;
        this.instantSource = instantSource;
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
                telephoneChargeRequest.getLastFourDigits().map(LastDigitsCardNumber::of).orElse(null),
                telephoneChargeRequest.getFirstSixDigits().map(FirstDigitsCardNumber::of).orElse(null),
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

    public Optional<ChargeResponse> create(ChargeCreateRequest chargeRequest, Long accountId, UriInfo uriInfo, String idempotencyKey) {
        try {
            return createCharge(chargeRequest, accountId, idempotencyKey)
                    .map(charge ->
                            populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, charge).build()
                    );
        } catch (RollbackException e) {
            if (idempotencyKey != null && idempotencyDao.findByGatewayAccountIdAndKey(accountId, idempotencyKey).isPresent()) {
                LOGGER.info("Race condition between two requests to create a charge with the same Idempotency Key handled");
                throw new IdempotencyKeyUsedException();
            }
            throw e;
        }
    }

    @Transactional
    private Optional<ChargeEntity> createCharge(ChargeCreateRequest chargeRequest, Long accountId, String idempotencyKey) {
        return gatewayAccountDao.findById(accountId).map(gatewayAccount -> {
            checkIfGatewayAccountDisabled(gatewayAccount);

            var authorisationMode = chargeRequest.getAuthorisationMode();

            if (authorisationMode == MOTO_API) {
                checkMotoApiAuthorisationModeAllowed(gatewayAccount);
            } else {
                checkIfMotoPaymentsAllowed(chargeRequest.isMoto(), gatewayAccount);
            }

            checkCardNumberInReferenceForPaymentLinkPayments(chargeRequest.getSource(), chargeRequest.getReference());

            checkAgreementOptions(chargeRequest, gatewayAccount);

            chargeRequest.getReturnUrl().ifPresent(returnUrl -> {
                if (gatewayAccount.isLive() && !returnUrl.startsWith("https://")) {
                    throw new ChargeException(format("Gateway account %d is LIVE, but is configured to use a " +
                            "non-https return_url", accountId), NON_HTTPS_RETURN_URL_NOT_ALLOWED_FOR_A_LIVE_ACCOUNT, 422);
                }
            });

            GatewayAccountCredentialsEntity gatewayAccountCredential =
                    getGatewayAccountCredentialsEntity(chargeRequest, gatewayAccount);

            checkIfAmountBelowMinimum(chargeRequest.getSource(), chargeRequest.getAmount(), gatewayAccount, gatewayAccountCredential.getPaymentProvider());

            var agreementEntity = Optional.ofNullable(chargeRequest.getAgreementId()).map(agreementId ->
                    agreementDao.findByExternalIdAndGatewayAccountId(chargeRequest.getAgreementId(), gatewayAccount.getId())
                            .orElseThrow(() -> new ChargeException("Agreement with ID [" + chargeRequest.getAgreementId() + "] not found.", AGREEMENT_NOT_FOUND, HttpStatus.SC_BAD_REQUEST)));

            AgreementPaymentType agreementPaymentType = null;
            if (chargeRequest.getSavePaymentInstrumentToAgreement() || chargeRequest.getAuthorisationMode() == AGREEMENT) {
                agreementPaymentType = chargeRequest.getAgreementPaymentType().orElse(AgreementPaymentType.RECURRING);
            }
 
            ChargeEntity.WebChargeEntityBuilder chargeEntityBuilder = aWebChargeEntity()
                    .withAmount(chargeRequest.getAmount())
                    .withDescription(chargeRequest.getDescription())
                    .withReference(ServicePaymentReference.of(chargeRequest.getReference()))
                    .withGatewayAccount(gatewayAccount)
                    .withGatewayAccountCredentialsEntity(gatewayAccountCredential)
                    .withPaymentProvider(gatewayAccountCredential.getPaymentProvider())
                    .withEmail(chargeRequest.getEmail().orElse(null))
                    .withLanguage(chargeRequest.getLanguage())
                    .withDelayedCapture(chargeRequest.isDelayedCapture())
                    .withExternalMetadata(chargeRequest.getExternalMetadata().orElse(null))
                    .withSource(chargeRequest.getSource())
                    .withMoto(authorisationMode == MOTO_API || chargeRequest.isMoto())
                    .withServiceId(gatewayAccount.getServiceId())
                    .withSavePaymentInstrumentToAgreement(chargeRequest.getSavePaymentInstrumentToAgreement())
                    .withAgreementEntity(agreementEntity.orElse(null))
                    .withAuthorisationMode(chargeRequest.getAuthorisationMode())
                    .withAgreementPaymentType(agreementPaymentType);

            chargeRequest.getReturnUrl().ifPresent(chargeEntityBuilder::withReturnUrl);
            ChargeEntity chargeEntity = chargeEntityBuilder.build();

            chargeRequest.getPrefilledCardHolderDetails()
                    .map(this::createCardDetailsEntity)
                    .ifPresent(chargeEntity::setCardDetails);

            if (authorisationMode == AuthorisationMode.AGREEMENT) {
                agreementEntity.ifPresent(agreement -> {
                    checkAgreementHasActivePaymentInstrument(agreement);
                    agreement.getPaymentInstrument()
                            .ifPresent(chargeEntity::setPaymentInstrument);
                });
            }

            chargeDao.persist(chargeEntity);

            if (authorisationMode == AGREEMENT && idempotencyKey != null) {
                Map<String, Object> requestBody = objectMapper.convertValue(chargeRequest, new TypeReference<>() {
                });
                IdempotencyEntity idempotencyEntity = new IdempotencyEntity(
                        idempotencyKey,
                        gatewayAccount,
                        chargeEntity.getExternalId(),
                        requestBody,
                        Instant.now());
                idempotencyDao.persist(idempotencyEntity);
            }

            transitionChargeState(chargeEntity, CREATED);
            chargeDao.merge(chargeEntity);

            return chargeEntity;
        });
    }

    private void checkCardNumberInReferenceForPaymentLinkPayments(Source source, String reference) {
        if (rejectPaymentLinkPaymentsWithCardNumberInReference != null && rejectPaymentLinkPaymentsWithCardNumberInReference
                && (source == CARD_PAYMENT_LINK || source == CARD_AGENT_INITIATED_MOTO)) {

            String referenceWithOutSpaceAndHyphen = reference.replaceAll("[ -]", "");

            if (referenceWithOutSpaceAndHyphen.length() >= 12 && referenceWithOutSpaceAndHyphen.length() <= 19
                    && LUHN_CHECK_DIGIT.isValid(referenceWithOutSpaceAndHyphen)) {

                cardidService.getCardInformation(referenceWithOutSpaceAndHyphen)
                        .ifPresent(cardInformation -> {
                            // Used by Splunk alert
                            LOGGER.info(
                                    "Card number entered in a payment link reference",
                                    kv("first_6_digits_reference", referenceWithOutSpaceAndHyphen.substring(0, 6)),
                                    kv("last_4_digits_reference", referenceWithOutSpaceAndHyphen.substring(referenceWithOutSpaceAndHyphen.length() - 4))
                            );
                            throw new ChargeException("Card number entered in a payment link reference", CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED, HttpStatus.SC_BAD_REQUEST);
                        });
            }
        }
    }

    private GatewayAccountCredentialsEntity getGatewayAccountCredentialsEntity(ChargeCreateRequest chargeRequest,
                                                                               GatewayAccountEntity gatewayAccount) {
        if (chargeRequest.getCredentialId() != null) {
            return gatewayAccountCredentialsService.getCredentialInUsableState(
                    chargeRequest.getCredentialId(), gatewayAccount.getId());
        }

        return gatewayAccountCredentialsService.getCurrentOrActiveCredential(gatewayAccount);
    }

    public Optional<ChargeResponse> checkForChargeCreatedWithIdempotencyKey(ChargeCreateRequest chargeRequest,
                                                                            Long gatewayAccountId,
                                                                            String idempotencyKey,
                                                                            UriInfo uriInfo) {
        return idempotencyDao.findByGatewayAccountIdAndKey(gatewayAccountId, idempotencyKey).map(idempotencyEntity -> {
            Map<String, Object> chargeRequestMap = objectMapper.convertValue(chargeRequest, new TypeReference<>() {
            });

            // Convert to `ChargeCreateRequest` then back to a Map. This is to handle the fact that when we read the
            // jsonb from the database, we read the amount as an integer, but on the `ChargeCreateRequest` it is a long.
            ChargeCreateRequest previousChargeRequest = objectMapper.convertValue(idempotencyEntity.getRequestBody(), ChargeCreateRequest.class);
            Map<String, Object> previousChargeRequestMap = objectMapper.convertValue(previousChargeRequest, new TypeReference<>() {
            });
            if (chargeRequestMap.equals(previousChargeRequestMap)) {
                LOGGER.info("Idempotency-Key was already used to create a request with matching values {}", idempotencyKey);
                ChargeEntity existingCharge = findChargeByExternalId(idempotencyEntity.getResourceExternalId());
                return populateResponseBuilderWith(aChargeResponseBuilder(), uriInfo, existingCharge).build();
            }

            LOGGER.info(format("Idempotency-Key [%s] was already used to create a charge with a different request body. Existing payment external id: %s",
                            idempotencyKey, idempotencyEntity.getResourceExternalId()),
                    kv("previous_request_body", previousChargeRequest.toStringWithoutPersonalIdentifiableInformation()),
                    kv("new_request_body", chargeRequest.toStringWithoutPersonalIdentifiableInformation()));
            throw new IdempotencyKeyUsedException();
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
    public Optional<ChargeResponse> findChargeForServiceIdAndAccountType(String chargeId, String serviceId, GatewayAccountType accountType, UriInfo uriInfo) {
        return chargeDao
                .findByExternalIdAndServiceIdAndAccountType(chargeId, serviceId, accountType)
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
                        eventService.emitAndRecordEvent(UserEmailCollected.from(chargeEntity, Instant.now()));
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
                .withChargeId(chargeEntity.getExternalId())
                .withAuthorisationMode(chargeEntity.getAuthorisationMode());

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
            authorisationSummary = getAuthorisationSummary(true, chargeEntity.get3dsRequiredDetails().getThreeDsVersion());
        } else if (chargeEntity.getRequires3ds() != null) {
            authorisationSummary = getAuthorisationSummary(chargeEntity.getRequires3ds(), null);
        } else if (chargeEntity.get3dsRequiredDetails() == null && chargeEntity.getRequires3ds() == null) {
            authorisationSummary = getAuthorisationSummary(false,  null);
        }

        T builderOfResponse = responseBuilder
                .withChargeId(chargeId)
                .withAmount(chargeEntity.getAmount())
                .withReference(chargeEntity.getReference())
                .withDescription(chargeEntity.getDescription())
                .withState(externalTransactionStateFactory.newExternalTransactionState(chargeEntity))
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
                .withAuthorisationMode(chargeEntity.getAuthorisationMode())
                .withExemption(buildExemptionResponseObject(chargeEntity.getExemption3ds(), chargeEntity.getExemption3dsRequested()))
                .withAgreementPaymentType(chargeEntity.getAgreementPaymentType());

        chargeEntity.getFeeAmount().ifPresent(builderOfResponse::withFee);
        chargeEntity.getAgreement().ifPresent(agreement -> builderOfResponse.withAgreementId(agreement.getExternalId()));
        chargeEntity.getExternalMetadata().ifPresent(builderOfResponse::withExternalMetadata);

        if (ChargeStatus.AWAITING_CAPTURE_REQUEST.getValue().equals(chargeEntity.getStatus())) {
            builderOfResponse.withLink("capture", POST, captureUriFor(uriInfo, chargeEntity.getGatewayAccount().getId(), chargeEntity.getExternalId()));
        }

        chargeEntity.getCorporateSurcharge().ifPresent(corporateSurcharge ->
                builderOfResponse.withCorporateCardSurcharge(corporateSurcharge)
                        .withTotalAmount(CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity)));

        chargeEntity.getNetAmount().ifPresent(builderOfResponse::withNetAmount);

        if (needsNextUrl(chargeEntity)) {
            TokenEntity token = createNewChargeEntityToken(chargeEntity);
            Map<String, Object> params = new HashMap<>();
            if (chargeEntity.getAuthorisationMode() == MOTO_API) {
                params.put("one_time_token", token.getToken());
                return builderOfResponse
                        .withLink("auth_url_post", POST, nextAuthUrl(uriInfo), APPLICATION_JSON, params);
            } else {
                params.put("chargeTokenId", token.getToken());

                return builderOfResponse
                        .withLink("next_url", GET, nextUrl(token.getToken()))
                        .withLink("next_url_post", POST, nextUrl(), APPLICATION_FORM_URLENCODED, params);
            }
        } else {
            return builderOfResponse;
        }
    }

    private ChargeResponse.Exemption buildExemptionResponseObject(Exemption3ds exemption3ds, Exemption3dsType exemption3dsRequested) {
        ChargeResponse.Exemption exemption = null;

        if (exemption3ds != null) {
            exemption = new ChargeResponse.Exemption();
            exemption.setRequested(exemption3ds != EXEMPTION_NOT_REQUESTED);

            if (exemption3ds != EXEMPTION_NOT_REQUESTED) {
                exemption.setType(exemption3dsRequested == CORPORATE ? CORPORATE.name().toLowerCase() : null);
                exemption.setOutcome(new ChargeResponse.Exemption.Outcome(exemption3ds));
            }
        } else if (exemption3dsRequested != null){
            exemption = new ChargeResponse.Exemption();
            exemption.setRequested(true);
            exemption.setType(exemption3dsRequested == CORPORATE ? CORPORATE.name().toLowerCase() : null);
        }
        return exemption;
    }

    private ChargeResponse.AuthorisationSummary getAuthorisationSummary(Boolean requires3ds, String version) {
        ChargeResponse.AuthorisationSummary authorisationSummary = new ChargeResponse.AuthorisationSummary();
        ChargeResponse.AuthorisationSummary.ThreeDSecure threeDSecure = new ChargeResponse.AuthorisationSummary.ThreeDSecure();
        threeDSecure.setRequired(requires3ds);
        threeDSecure.setVersion(version);
        authorisationSummary.setThreeDSecure(threeDSecure);
        return authorisationSummary;
    }

    private boolean needsNextUrl(ChargeEntity chargeEntity) {
        if (chargeEntity.getAuthorisationMode() == AuthorisationMode.AGREEMENT) {
            return false;
        }
        ChargeStatus chargeStatus = ChargeStatus.fromString(chargeEntity.getStatus());
        return !chargeStatus.toExternal().isFinished() && !chargeStatus.equals(AWAITING_CAPTURE_REQUEST);
    }

    public ChargeEntity updateChargePostCardAuthorisation(String chargeExternalId,
                                                          ChargeStatus newStatus,
                                                          String transactionId,
                                                          Auth3dsRequiredEntity auth3dsRequiredDetails,
                                                          ProviderSessionIdentifier sessionIdentifier,
                                                          AuthCardDetails authCardDetails,
                                                          Map<String, String> recurringAuthToken,
                                                          Boolean canRetry,
                                                          String rejectedReason) {
        return updateChargeAndEmitEventPostAuthorisation(chargeExternalId, newStatus, authCardDetails, transactionId, auth3dsRequiredDetails, sessionIdentifier,
                null, null, recurringAuthToken, canRetry, rejectedReason);

    }

    public ChargeEntity updateChargePostWalletAuthorisation(String chargeExternalId,
                                                            ChargeStatus status,
                                                            String transactionId,
                                                            ProviderSessionIdentifier sessionIdentifier,
                                                            AuthCardDetails authCardDetails,
                                                            WalletType walletType,
                                                            String emailAddress,
                                                            Auth3dsRequiredEntity auth3dsRequiredDetails) {
        return updateChargeAndEmitEventPostAuthorisation(chargeExternalId, status, authCardDetails, transactionId, auth3dsRequiredDetails, sessionIdentifier,
                walletType, emailAddress, null, null, null);
    }

    private ChargeEntity updateChargeAndEmitEventPostAuthorisation(String chargeExternalId,
                                                                   ChargeStatus newStatus,
                                                                   AuthCardDetails authCardDetails,
                                                                   String transactionId,
                                                                   Auth3dsRequiredEntity auth3dsRequiredDetails,
                                                                   ProviderSessionIdentifier sessionIdentifier,
                                                                   WalletType walletType,
                                                                   String emailAddress,
                                                                   Map<String, String> recurringAuthToken,
                                                                   Boolean canRetry,
                                                                   String rejectedReason) {
        updateChargePostAuthorisation(chargeExternalId, newStatus, authCardDetails, transactionId,
                auth3dsRequiredDetails, sessionIdentifier, walletType, emailAddress, recurringAuthToken, canRetry, rejectedReason);
        ChargeEntity chargeEntity = findChargeByExternalId(chargeExternalId);
        if (chargeEntity.getAuthorisationMode() == MOTO_API) {
            eventService.emitAndRecordEvent(PaymentDetailsSubmittedByAPI.from(chargeEntity));
        } else if (chargeEntity.getAuthorisationMode() == AGREEMENT) {
            eventService.emitAndRecordEvent(PaymentDetailsTakenFromPaymentInstrument.from(chargeEntity));
        } else {
            eventService.emitAndRecordEvent(PaymentDetailsEntered.from(chargeEntity));
        }
        if (Boolean.FALSE.equals(chargeEntity.getRequires3ds())) {
            eventService.emitAndRecordEvent(GatewayDoesNotRequire3dsAuthorisation.from(chargeEntity, instantSource.instant()));
        }

        return chargeEntity;
    }

    // cannot be private: Guice requires @Transactional methods to be public
    @Transactional
    public ChargeEntity updateChargePostAuthorisation(String chargeExternalId,
                                                      ChargeStatus newStatus,
                                                      AuthCardDetails authCardDetails,
                                                      String transactionId,
                                                      Auth3dsRequiredEntity auth3dsRequiredDetails,
                                                      ProviderSessionIdentifier sessionIdentifier,
                                                      WalletType walletType,
                                                      String emailAddress,
                                                      Map<String, String> recurringAuthToken,
                                                      Boolean canRetry,
                                                      String rejectedReason) {
        return chargeDao.findByExternalId(chargeExternalId).map(charge -> {
            setTransactionId(charge, transactionId);
            Optional.ofNullable(sessionIdentifier).map(ProviderSessionIdentifier::toString).ifPresent(charge::setProviderSessionId);
            Optional.ofNullable(auth3dsRequiredDetails).ifPresent(charge::set3dsRequiredDetails);
            Optional.ofNullable(walletType).ifPresent(charge::setWalletType);
            Optional.ofNullable(emailAddress).ifPresent(charge::setEmail);

            CardDetailsEntity detailsEntity = authCardDetailsToCardDetailsEntityConverter.convert(authCardDetails);

            // propagate details that aren't mapped from payment instrument to auth card details onto the charge
            // this logic should be removable when payment instruments are modelled and used for all authorisation types
            if (charge.getAuthorisationMode() == AuthorisationMode.AGREEMENT) {
                charge.getPaymentInstrument()
                        .ifPresent(paymentInstrument -> {
                            detailsEntity.setFirstDigitsCardNumber(paymentInstrument.getCardDetails().getFirstDigitsCardNumber());
                            detailsEntity.setLastDigitsCardNumber(paymentInstrument.getCardDetails().getLastDigitsCardNumber());
                        });

                charge.setCanRetry(canRetry);

                if (canRetry != null && !canRetry) {
                    inactivateAgreement(charge, rejectedReason);
                }
            }
            charge.setCardDetails(detailsEntity);

            if (charge.isSavePaymentInstrumentToAgreement()) {
                Optional.ofNullable(recurringAuthToken).ifPresent(token -> setPaymentInstrument(token, charge));
            }
            
            if (newStatus == AUTHORISATION_SUCCESS || newStatus == AUTHORISATION_REJECTED) {
                if (!Boolean.TRUE.equals(charge.getRequires3ds())) {
                    charge.setRequires3ds(false);
                }
            }

            transitionChargeState(charge, newStatus);

            LOGGER.info("Stored confirmation details for charge - charge_external_id={}",
                    chargeExternalId);

            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
    }

    private void inactivateAgreement(ChargeEntity charge, String rejectedReason) {
        charge.getAgreement().ifPresent(agreementEntity -> {
            AgreementInactivated inactivatedEvent = AgreementInactivated
                    .from(agreementEntity, rejectedReason, Instant.now());
            ledgerService.postEvent(inactivatedEvent);
        });
        charge.getPaymentInstrument().ifPresent(paymentInstrumentEntity ->
                paymentInstrumentEntity.setStatus(PaymentInstrumentStatus.INACTIVE)
        );
    }

    @Transactional
    public ChargeEntity updateChargePost3dsAuthorisation(String chargeExternalId, ChargeStatus status,
                                                         OperationType operationType,
                                                         String transactionId,
                                                         Auth3dsRequiredEntity auth3dsRequiredDetails,
                                                         ProviderSessionIdentifier sessionIdentifier,
                                                         Map<String, String> recurringAuthToken) {
        return chargeDao.findByExternalId(chargeExternalId).map(charge -> {
            try {
                setTransactionId(charge, transactionId);
                transitionChargeState(charge, status);
                Optional.ofNullable(auth3dsRequiredDetails).ifPresent(charge::set3dsRequiredDetails);
                Optional.ofNullable(sessionIdentifier).map(ProviderSessionIdentifier::toString).ifPresent(charge::setProviderSessionId);
                if (charge.isSavePaymentInstrumentToAgreement()) {
                    Optional.ofNullable(recurringAuthToken).ifPresent(token -> setPaymentInstrument(token, charge));
                }
            } catch (InvalidStateTransitionException e) {
                if (chargeIsInLockedStatus(operationType, charge)) {
                    throw new OperationAlreadyInProgressRuntimeException(operationType.getValue(), charge.getExternalId());
                }
                throw new IllegalStateRuntimeException(charge.getExternalId());
            }

            if (auth3dsRequiredDetails != null && isNotBlank(auth3dsRequiredDetails.getThreeDsVersion())) {
                eventService.emitAndRecordEvent(Gateway3dsInfoObtained.from(charge, Instant.now()));
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

    @Transactional
    public void markChargeAsEligibleForAuthoriseUserNotPresent(String chargeExternalId) {
        var charge = findChargeByExternalId(chargeExternalId);
        transitionChargeState(charge, AUTHORISATION_USER_NOT_PRESENT_QUEUED);
        taskQueueService.addAuthoriseWithUserNotPresentTask(charge);
    }

    private void setTransactionId(ChargeEntity chargeEntity, String transactionId) {
        if (transactionId != null && !transactionId.isBlank()) {
            chargeEntity.setGatewayTransactionId(transactionId);
        }
    }

    private void setPaymentInstrument(Map<String, String> recurringAuthToken, ChargeEntity charge) {
        var paymentInstrument = paymentInstrumentService.createPaymentInstrument(charge, recurringAuthToken);
        charge.setPaymentInstrument(paymentInstrument);
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

    public Integer getLongestDurationOfChargesAwaitingCaptureInMinutes(int chargesConsideredOverdueForCaptureAfterMinutes) {
        Instant earliestDate = chargeDao.getEarliestUpdatedDateOfChargesReadyForImmediateCapture(
                chargesConsideredOverdueForCaptureAfterMinutes);

        if (earliestDate != null) {
            return (int) Duration.between(earliestDate, Instant.now()).toMinutes();
        }

        return null;
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

        charge.setUpdatedDate(Instant.now());
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
            charge.setUpdatedDate(Instant.now());
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

        settlement.setCaptureSubmitTime(Optional.ofNullable(charge.getCaptureSubmitTime()).map(ZonedDateTime::toInstant).orElse(null));
        settlement.setCapturedTime(Optional.ofNullable(charge.getCapturedTime()).map(ZonedDateTime::toInstant).orElse(null));

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

    private URI nextAuthUrl(UriInfo uriInfo) {
        return uriInfo.getBaseUriBuilder()
                .path("/v1/api/charges/authorise")
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

    private void checkMotoApiAuthorisationModeAllowed(GatewayAccountEntity gatewayAccount) {
        if (!gatewayAccount.isAllowAuthorisationApi()) {
            throw new AuthorisationApiNotAllowedForGatewayAccountException(gatewayAccount.getId());
        }
    }
    
    private void checkIfGatewayAccountDisabled(GatewayAccountEntity gatewayAccount) {
        if (gatewayAccount.isDisabled()) {
            throw new GatewayAccountDisabledException("Attempt to create a charge for a disabled gateway account");
        }
    }

    private void checkIfAmountBelowMinimum(Source source, Long amount, GatewayAccountEntity gatewayAccount, String paymentProvider) {
        if (source != CARD_EXTERNAL_TELEPHONE && amount < MINIMUM_STRIPE_PAYMENT_AMOUNT && PaymentGatewayName.valueFrom(paymentProvider) == STRIPE) {
            throw new ChargeException("Payments under 30 pence are not allowed for Stripe accounts", AMOUNT_BELOW_MINIMUM, SC_UNPROCESSABLE_ENTITY);
        }
        checkIfZeroAmountAllowed(amount, gatewayAccount);
    }
    
    private void checkIfZeroAmountAllowed(Long amount, GatewayAccountEntity gatewayAccount) {
        if (amount == 0L && !gatewayAccount.isAllowZeroAmount()) {
            throw new ZeroAmountNotAllowedForGatewayAccountException(gatewayAccount.getId());
        }
    }

    private void checkIfMotoPaymentsAllowed(boolean moto, GatewayAccountEntity gatewayAccount) {
        if (moto && !gatewayAccount.isAllowMoto()) {
            throw new ChargeException("MOTO payments are not enabled for this gateway account", MOTO_NOT_ALLOWED, SC_UNPROCESSABLE_ENTITY);
        }
    }

    private void checkAgreementOptions(ChargeCreateRequest chargeCreateRequest, GatewayAccountEntity gatewayAccount) {
        switch (chargeCreateRequest.getAuthorisationMode()) {
            case AGREEMENT -> {
                if (!gatewayAccount.isRecurringEnabled()) {
                    throw new RecurringCardPaymentsNotAllowedException(
                            "Attempt to use authorisation mode 'agreement' for gateway account " +
                                    gatewayAccount.getId() +
                                    ", which does not have recurring card payments enabled");
                } else if (chargeCreateRequest.getAgreementId() == null) {
                    throw new MissingMandatoryAttributeException("agreement_id");
                } else if (chargeCreateRequest.getSavePaymentInstrumentToAgreement()) {
                    throw new IncorrectAuthorisationModeForSavePaymentToAgreementException();
                } else if (chargeCreateRequest.isMoto()) {
                    throw new UnexpectedAttributeException("moto");
                } else if (chargeCreateRequest.getEmail().isPresent()) {
                    throw new UnexpectedAttributeException("email");
                } else if (chargeCreateRequest.getPrefilledCardHolderDetails().isPresent()) {
                    throw new UnexpectedAttributeException("prefilled_cardholder_details");
                }
            }
            case WEB -> {
                if (chargeCreateRequest.getAgreementId() != null) {
                    if (!chargeCreateRequest.getSavePaymentInstrumentToAgreement()) {
                        throw new UnexpectedAttributeException("agreement_id");
                    } else if (!gatewayAccount.isRecurringEnabled()) {
                        throw new RecurringCardPaymentsNotAllowedException(
                                "Attempt to save payment instrument to agreement for gateway account " +
                                        gatewayAccount.getId() +
                                        ", which does not have recurring card payments enabled");
                    }
                } else {
                    if (chargeCreateRequest.getSavePaymentInstrumentToAgreement()) {
                        throw new ChargeException("If [save_payment_instrument_to_agreement] is true, [agreement_id] must be specified",
                                GENERIC, HttpStatus.SC_BAD_REQUEST);
                    } else if (chargeCreateRequest.getAgreementPaymentType().isPresent()) {
                        throw new UnexpectedAttributeException("agreement_payment_type");
                    }
                }
            }
            default -> {
                if (chargeCreateRequest.getAgreementId() != null) {
                    throw new UnexpectedAttributeException("agreement_id");
                } else if (chargeCreateRequest.getSavePaymentInstrumentToAgreement()) {
                    throw new IncorrectAuthorisationModeForSavePaymentToAgreementException();
                } else if (chargeCreateRequest.getAgreementPaymentType().isPresent()) {
                    throw new UnexpectedAttributeException("agreement_payment_type");
                }
            }
        }
    }

    private void checkAgreementHasActivePaymentInstrument(AgreementEntity agreementEntity) {
        var paymentInstrumentEntity = agreementEntity.getPaymentInstrument()
                .orElseThrow(() -> new ChargeException("Agreement with ID [" + agreementEntity.getExternalId() +
                        "] does not have a payment instrument", AGREEMENT_NOT_ACTIVE, HttpStatus.SC_BAD_REQUEST));

        if (paymentInstrumentEntity.getStatus() != PaymentInstrumentStatus.ACTIVE) {
            throw new ChargeException("Agreement with ID [" + agreementEntity.getExternalId() + "] has payment instrument with ID [" +
                    paymentInstrumentEntity.getExternalId() + "] but its state is [" + paymentInstrumentEntity.getStatus() + "]" , 
                    AGREEMENT_NOT_ACTIVE, HttpStatus.SC_BAD_REQUEST);
        }
    }

    public RefundAvailabilityUpdated createRefundAvailabilityUpdatedEvent(Charge charge, Instant eventTimestamp) {
        List<Refund> refundList = refundService.findRefunds(charge);
        ExternalChargeRefundAvailability refundAvailability;

        refundAvailability = providers
                .byName(PaymentGatewayName.valueFrom(charge.getPaymentGatewayName()))
                .getExternalChargeRefundAvailability(charge, refundList);

        return new RefundAvailabilityUpdated(
                charge.getServiceId(),
                charge.isLive(),
                charge.getGatewayAccountId(),
                charge.getExternalId(),
                RefundAvailabilityUpdatedEventDetails.from(
                        charge,
                        refundList,
                        refundAvailability
                ),
                eventTimestamp
        );
    }

    public FrontendChargeResponse buildChargeResponse(UriInfo uriInfo, ChargeEntity charge) {
        String chargeId = charge.getExternalId();
        FrontendChargeResponse.FrontendChargeResponseBuilder responseBuilder = aFrontendChargeResponse()
                .withStatus(charge, externalTransactionStateFactory)
                .withChargeId(chargeId)
                .withAmount(charge.getAmount())
                .withDescription(charge.getDescription())
                .withProviderName(charge.getPaymentProvider())
                .withGatewayTransactionId(charge.getGatewayTransactionId())
                .withCreatedDate(charge.getCreatedDate())
                .withReturnUrl(charge.getReturnUrl())
                .withEmail(charge.getEmail())
                .withFee(charge.getFeeAmount().orElse(null))
                .withNetAmount(charge.getNetAmount().orElse(null))
                .withGatewayAccount(charge.getGatewayAccount())
                .withLanguage(charge.getLanguage())
                .withDelayedCapture(charge.isDelayedCapture())
                .withSavePaymentInstrumentToAgreement(charge.isSavePaymentInstrumentToAgreement())
                .withLink("self", GET, locationUriFor("/v1/frontend/charges/{chargeId}", uriInfo, chargeId))
                .withLink("cardAuth", POST, locationUriFor("/v1/frontend/charges/{chargeId}/cards", uriInfo, chargeId))
                .withLink("cardCapture", POST, locationUriFor("/v1/frontend/charges/{chargeId}/capture", uriInfo, chargeId))
                .withWalletType(charge.getWalletType())
                .withMoto(charge.isMoto())
                .withAuthorisationMode(charge.getAuthorisationMode())
                .withPaymentConfirmationEmailEnabled(
                        Optional.ofNullable(charge.getGatewayAccount()
                                        .getEmailNotifications().get(PAYMENT_CONFIRMED))
                                .map(EmailNotificationEntity::isEnabled)
                                .orElse(false));

        if (charge.getCardDetails() != null) {
            var persistedCard = charge.getCardDetails().toCard();
            persistedCard.setCardBrand(findCardBrandLabel(charge.getCardDetails().getCardBrand()).orElse(""));
            responseBuilder.withCardDetails(persistedCard);
        }

        charge.getAgreement().ifPresent(agreementEntity -> responseBuilder.withAgreementId(agreementEntity.getExternalId()));
        charge.getAgreement().map(AgreementResponse::from).ifPresent(responseBuilder::withAgreement);

        if (charge.get3dsRequiredDetails() != null) {
            var auth3dsData = new ChargeResponse.Auth3dsData();
            auth3dsData.setPaRequest(charge.get3dsRequiredDetails().getPaRequest());
            auth3dsData.setIssuerUrl(charge.get3dsRequiredDetails().getIssuerUrl());
            auth3dsData.setHtmlOut(charge.get3dsRequiredDetails().getHtmlOut());
            auth3dsData.setMd(charge.get3dsRequiredDetails().getMd());

            if (WORLDPAY.getName().equals(charge.getPaymentProvider())) {
                worldpay3dsFlexJwtService.generateChallengeTokenIfAppropriate(charge).ifPresent(
                        auth3dsData::setWorldpayChallengeJwt);
            }

            responseBuilder.withAuth3dsData(auth3dsData);
        }

        charge.getCorporateSurcharge().ifPresent(surcharge -> {
            if (surcharge > 0) {
                responseBuilder
                        .withCorporateCardSurcharge(surcharge)
                        .withTotalAmount(CorporateCardSurchargeCalculator.getTotalAmountFor(charge));
            }
        });

        return responseBuilder.build();
    }

    private URI locationUriFor(String path, UriInfo uriInfo, String chargeId) {
        return uriInfo.getBaseUriBuilder()
                .path(path)
                .build(chargeId);
    }

}
