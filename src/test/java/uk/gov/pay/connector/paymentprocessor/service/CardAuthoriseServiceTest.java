package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.Counter;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeEntityBuilder;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.exception.motoapi.AuthorisationTimedOutException;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeEligibleForCaptureService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.LinkPaymentInstrumentToAgreementService;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.gateway.sandbox.SandboxAuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.logging.AuthorisationLogger;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.paymentprocessor.api.AuthorisationResponse;
import uk.gov.pay.connector.paymentprocessor.model.AuthoriseRequest;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_QUEUED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.IN_PROGRESS;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

@ExtendWith(MockitoExtension.class)
class CardAuthoriseServiceTest extends CardServiceTest {

    private static final ProviderSessionIdentifier SESSION_IDENTIFIER = ProviderSessionIdentifier.of("session-identifier");
    private static final String TRANSACTION_ID = "transaction-id";

    private final ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
    private final String chargeExternalId = charge.getExternalId();

    @Mock
    private CardExecutorService mockExecutorService;

    @Mock
    private Environment mockEnvironment;

    @Mock
    private Counter mockCounter;

    @Mock
    private StateTransitionService stateTransitionService;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private EventService mockEventService;

    @Mock
    private PaymentInstrumentService mockPaymentInstrumentService;

    @Mock
    private RefundService mockRefundService;

    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;

    @Mock
    private AuthCardDetailsToCardDetailsEntityConverter mockAuthCardDetailsToCardDetailsEntityConverter;

    @Mock
    private AuthorisationRequestSummaryStringifier mockAuthorisationRequestSummaryStringifier;

    @Mock
    private AuthorisationRequestSummaryStructuredLogging mockAuthorisationRequestSummaryStructuredLogging;

    @Mock
    private TaskQueueService mockTaskQueueService;

    @Mock
    private ChargeEligibleForCaptureService mockChargeEligibleForCaptureService;

    @Mock
    private ConnectorConfiguration mockConfiguration;

    @Mock
    private AuthorisationConfig mockAuthorisationConfig;

    private CardAuthoriseService cardAuthorisationService;
    private final CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(
            LastDigitsCardNumber.of("1234"),
            FirstDigitsCardNumber.of("123456"),
            "Jane Doe",
            CardExpiryDate.valueOf("01/19"),
            "visa",
            CardType.valueOf("DEBIT")
    );

    @BeforeEach
    void setUpCardAuthorisationService() {
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockedPaymentProvider.generateAuthorisationRequestSummary(any(GatewayAccountEntity.class), any(AuthCardDetails.class)))
                .thenReturn(new SandboxAuthorisationRequestSummary());
        when(mockConfiguration.getAuthorisationConfig()).thenReturn(mockAuthorisationConfig);

        ChargeService chargeService = new ChargeService(null, mockedChargeDao, mockedChargeEventDao,
                null, null, null, mockConfiguration, null,
                stateTransitionService, ledgerService, mockRefundService, mockEventService, mockPaymentInstrumentService,
                mockGatewayAccountCredentialsService, mockAuthCardDetailsToCardDetailsEntityConverter, mockTaskQueueService);

        LinkPaymentInstrumentToAgreementService linkPaymentInstrumentToAgreementService = mock(LinkPaymentInstrumentToAgreementService.class);
        CaptureQueue captureQueue = mock(CaptureQueue.class);
        UserNotificationService userNotificationService = mock(UserNotificationService.class);
        ChargeEligibleForCaptureService chargeEligibleForCaptureService =
                new ChargeEligibleForCaptureService(chargeService, mockedChargeDao, linkPaymentInstrumentToAgreementService,
                        captureQueue,
                        userNotificationService
                );

        AuthorisationService authorisationService = new AuthorisationService(mockExecutorService, mockEnvironment, mockConfiguration);
        cardAuthorisationService = new CardAuthoriseService(
                mockedCardTypeDao,
                mockedProviders,
                authorisationService,
                chargeService,
                new AuthorisationLogger(mockAuthorisationRequestSummaryStringifier, mockAuthorisationRequestSummaryStructuredLogging),
                chargeEligibleForCaptureService,
                mockEnvironment);
    }

    void mockRecordAuthorisationResult() {
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockAuthorisationRequestSummaryStringifier.stringify(any(AuthorisationRequestSummary.class))).thenReturn("");
    }

    void mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue() {
        doAnswer(invocation -> Pair.of(COMPLETED, ((Supplier) invocation.getArguments()[0]).get()))
                .when(mockExecutorService).execute(any(Supplier.class), anyInt());
    }

    private GatewayResponse mockProviderRespondedSuccessfullyResponse(String transactionId, AuthoriseStatus authoriseStatus) {
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        when(worldpayResponse.getTransactionId()).thenReturn(transactionId);
        when(worldpayResponse.authoriseStatus()).thenReturn(authoriseStatus);
        GatewayResponseBuilder<WorldpayOrderStatusResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder
                .withResponse(worldpayResponse)
                .withSessionIdentifier(SESSION_IDENTIFIER)
                .build();
    }

    private GatewayResponse mockAuthErrorResponse(AuthoriseStatus authoriseStatus, String errorCode) {
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        GatewayResponseBuilder<WorldpayOrderStatusResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder
                .withResponse(worldpayResponse)
                .withSessionIdentifier(SESSION_IDENTIFIER)
                .build();
    }

    @Test
    void doAuthorise_shouldPublishEvent() throws Exception {
        mockRecordAuthorisationResult();
        providerWillAuthorise();
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(null)
                .build();

        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockEventService, times(1)).emitAndRecordEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getResourceExternalId(), is(charge.getExternalId()));
        assertThat(eventCaptor.getValue().getEventType(), is("PAYMENT_DETAILS_ENTERED"));
    }

    @Test
    void doAuthoriseWithNonCorporateCard_shouldRespondAuthorisationSuccess() throws Exception {
        mockRecordAuthorisationResult();
        providerWillAuthorise();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(cardDetailsEntity);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getCardDetails(), is(cardDetailsEntity));
        assertThat(charge.getCorporateSurcharge().isPresent(), is(false));
    }

    @Test
    void doAuthoriseWithCorporateCard_shouldRespondAuthorisationSuccess_whenNoCorporateSurchargeSet() throws Exception {
        mockRecordAuthorisationResult();
        providerWillAuthorise();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCorporateCard(Boolean.TRUE)
                .withCardType(PayersCardType.CREDIT)
                .build();

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(cardDetailsEntity);

        charge.getGatewayAccount().setCorporateCreditCardSurchargeAmount(0L);

        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getCardDetails(), is(cardDetailsEntity));
        assertThat(charge.getCorporateSurcharge().isPresent(), is(false));
    }

    @Test
    void doAuthoriseWithCreditCorporateSurcharge_shouldRespondAuthorisationSuccess() throws Exception {
        mockRecordAuthorisationResult();
        providerWillAuthorise();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCorporateCard(Boolean.TRUE)
                .withCardType(PayersCardType.CREDIT)
                .withPayersCardPrepaidStatus(PayersCardPrepaidStatus.NOT_PREPAID)
                .build();

        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(cardDetailsEntity);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        charge.getGatewayAccount().setCorporateCreditCardSurchargeAmount(250L);
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getCardDetails(), is(cardDetailsEntity));
        assertThat(charge.getCorporateSurcharge().get(), is(250L));
    }

    @Test
    void doAuthoriseWithDebitCorporateSurcharge_shouldRespondAuthorisationSuccess() throws Exception {
        mockRecordAuthorisationResult();
        providerWillAuthorise();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCorporateCard(Boolean.TRUE)
                .withCardType(PayersCardType.DEBIT)
                .withPayersCardPrepaidStatus(PayersCardPrepaidStatus.NOT_PREPAID)
                .build();

        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(cardDetailsEntity);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        charge.getGatewayAccount().setCorporateDebitCardSurchargeAmount(50L);
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getCardDetails(), is(cardDetailsEntity));
        assertThat(charge.getCorporateSurcharge().get(), is(50L));
        assertThat(charge.getWalletType(), is(nullValue()));
    }

    @ParameterizedTest()
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "WEB")
    void doAuthoriseShouldIgnoreCorporateCardSurchargeForChargeWithNonWebAuthorisationMode(AuthorisationMode authorisationMode) throws Exception {
        mockRecordAuthorisationResult();
        providerWillAuthorise();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCorporateCard(Boolean.TRUE)
                .withCardType(PayersCardType.DEBIT)
                .withPayersCardPrepaidStatus(PayersCardPrepaidStatus.NOT_PREPAID)
                .build();

        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("424242"), LastDigitsCardNumber.of("4242"),
                "Mr Test", CardExpiryDate.valueOf("12/99"), "VISA", CardType.DEBIT, null);
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(cardDetailsEntity);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        charge.setAuthorisationMode(authorisationMode);
        charge.getGatewayAccount().setCorporateDebitCardSurchargeAmount(50L);

        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());

        assertThat(charge.getCorporateSurcharge().isPresent(), is(false));
    }

    @Test
    void doAuthorise_shouldRespondAuthorisationSuccess_overridingGeneratedTransactionId() throws Exception {
        mockRecordAuthorisationResult();
        providerWillAuthorise();

        String generatedTransactionId = "this-will-be-override-to-TRANSACTION-ID-from-provider";

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getWalletType(), is(nullValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
    }

    @Test
    void doAuthorise_shouldRespondWith3dsResponseForEpdq3dsOrders() throws Exception {
        epdqProviderWillRequire3ds();
        mockRecordAuthorisationResult();
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.REQUIRES_3DS));

        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails().getHtmlOut(), is(notNullValue()));
        assertThat(charge.getWalletType(), is(nullValue()));
    }

    @Test
    void doAuthorise_shouldRetainGeneratedTransactionId_WhenProviderAuthorisationFails() throws Exception {

        String generatedTransactionId = "generated-transaction-id";
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        when(mockedPaymentProvider.authorise(any(), any())).thenThrow(RuntimeException.class);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        
        assertThrows(RuntimeException.class, () -> cardAuthorisationService.doAuthorise(chargeExternalId, authCardDetails));
        assertThat(charge.getGatewayTransactionId(), is(generatedTransactionId));
    }

    @Test
    void doAuthorise_shouldRespondAuthorisationFailed_When3dsRequiredConflictingConfigurationOfCardTypeWithGatewayAccount() {

        AuthCardDetails authCardDetails = AuthCardDetailsFixture
                .anAuthCardDetails()
                .build();
        CardTypeEntity cardTypeEntity = CardTypeEntityBuilder
                .aCardTypeEntity()
                .withRequires3ds(true)
                .withBrand(authCardDetails.getCardBrand())
                .build();

        GatewayAccountEntity gatewayAccountEntity = GatewayAccountEntityFixture
                .aGatewayAccountEntity()
                .withType(GatewayAccountType.LIVE)
                .withGatewayName(WORLDPAY.getName())
                .withRequires3ds(false)
                .build();

        ChargeEntity charge = ChargeEntityFixture
                .aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withStatus(ENTERING_CARD_DETAILS)
                .build();
        String chargeWithConflicting3dsId = charge.getExternalId();

        when(mockedCardTypeDao.findByBrand(authCardDetails.getCardBrand())).thenReturn(newArrayList(cardTypeEntity));
        when(mockedChargeDao.findByExternalId(chargeWithConflicting3dsId)).thenReturn(Optional.of(charge));

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        
        assertThrows(IllegalStateRuntimeException.class, () -> cardAuthorisationService.doAuthorise(chargeWithConflicting3dsId, authCardDetails));
        assertThat(charge.getStatus(), is(AUTHORISATION_ABORTED.toString()));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
    }

    @Test
    void doAuthorise_shouldRespondAuthorisationRejected_whenProviderAuthorisationIsRejected() throws Exception {
        mockRecordAuthorisationResult();
        providerWillReject();
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.REJECTED));

        assertThat(charge.getStatus(), is(AUTHORISATION_REJECTED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
    }

    @Test
    void doAuthorise_shouldRespondAuthorisationCancelled_whenProviderAuthorisationIsCancelled() throws Exception {

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockProviderRespondedSuccessfullyResponse(TRANSACTION_ID, AuthoriseStatus.CANCELLED);
        mockRecordAuthorisationResult();
        providerWillRespondToAuthoriseWith(authResponse);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.CANCELLED));

        assertThat(charge.getStatus(), is(AUTHORISATION_CANCELLED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
    }

    @Test
    void shouldRespondAuthorisationError() throws Exception {
        mockRecordAuthorisationResult();
        providerWillError();
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getGatewayError().isPresent());
        assertThat(response.getGatewayError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));

        assertThat(charge.getStatus(), is(AUTHORISATION_ERROR.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(nullValue()));
    }

    @Test
    void doAuthorise_shouldStoreCardDetails_evenIfAuthorisationRejected() throws Exception {
        mockRecordAuthorisationResult();
        providerWillReject();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(cardDetailsEntity);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(notNullValue()));
    }

    @Test
    void doAuthorise_shouldStoreCardDetails_evenIfInAuthorisationError() throws Exception {
        mockRecordAuthorisationResult();
        providerWillError();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(cardDetailsEntity);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));


        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(notNullValue()));
    }

    @Test
    void doAuthorise_shouldStoreProviderSessionId_evenIfAuthorisationRejected() throws Exception {
        mockRecordAuthorisationResult();
        providerWillReject();
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
    }

    @Test
    void doAuthorise_shouldNotProviderSessionId_whenAuthorisationError() throws Exception {
        mockRecordAuthorisationResult();
        providerWillError();
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertThat(charge.getProviderSessionId(), is(nullValue()));
    }

    @Test
    void doAuthorise_shouldThrowAnOperationAlreadyInProgressRuntimeException_whenTimeout() {
        when(mockAuthorisationConfig.getAsynchronousAuthTimeoutInMilliseconds()).thenReturn(1000);
        when(mockExecutorService.execute(any(), anyInt())).thenReturn(Pair.of(IN_PROGRESS, null));
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();

        OperationAlreadyInProgressRuntimeException e = assertThrows(OperationAlreadyInProgressRuntimeException.class, () -> cardAuthorisationService.doAuthorise(chargeExternalId, authCardDetails));
        ErrorResponse response = (ErrorResponse) e.getResponse().getEntity();
        assertThat(response.getMessages(), contains(format("Authorisation for charge already in progress, %s", charge.getExternalId())));
    }

    @Test
    void doAuthorise_shouldThrowAChargeNotFoundRuntimeException_whenChargeDoesNotExist() {

        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        assertThrows(ChargeNotFoundRuntimeException.class, () -> cardAuthorisationService.doAuthorise(chargeId, authCardDetails));
    }

    @Test
    void doAuthorise_shouldThrowAnOperationAlreadyInProgressRuntimeException_whenStatusIsAuthorisationReady() {

        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_READY);
        String inProgressChargeId = charge.getExternalId();
        when(mockedChargeDao.findByExternalId(inProgressChargeId)).thenReturn(Optional.of(charge));
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        assertThrows(OperationAlreadyInProgressRuntimeException.class, () -> cardAuthorisationService.doAuthorise(inProgressChargeId, authCardDetails));

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test
    void doAuthorise_shouldThrowAnIllegalStateRuntimeException_whenInvalidStatus() {

        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.UNDEFINED);
        String chargeWithInvalidStatusId = charge.getExternalId();
        when(mockedChargeDao.findByExternalId(chargeWithInvalidStatusId)).thenReturn(Optional.of(charge));
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        assertThrows(IllegalStateRuntimeException.class, () -> cardAuthorisationService.doAuthorise(chargeWithInvalidStatusId, authCardDetails));

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test
    void doAuthorise_shouldReportAuthorisationTimeout_whenProviderTimeout() throws Exception {
        mockRecordAuthorisationResult();
        providerWillRespondWithError(new GatewayException.GatewayConnectionTimeoutException("Connection timed out"));
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getGatewayError().isPresent());
        assertThat(response.getGatewayError().get().getErrorType(), is(GATEWAY_CONNECTION_TIMEOUT_ERROR));

        assertThat(charge.getStatus(), is(AUTHORISATION_TIMEOUT.getValue()));
    }

    @Test
    void doAuthorise_shouldReportUnexpectedError_whenProviderError() throws Exception {
        mockRecordAuthorisationResult();
        providerWillRespondWithError(new GatewayException.GatewayErrorException("Malformed response received"));
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        AuthorisationResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), authCardDetails);

        assertTrue(response.getGatewayError().isPresent());
        assertThat(response.getGatewayError().get().getErrorType(), is(GATEWAY_ERROR));
        assertThat(charge.getStatus(), is(AUTHORISATION_UNEXPECTED_ERROR.getValue()));
    }

    @Test
    void doAuthoriseMotoApi_shouldPublishEvent() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        mockRecordAuthorisationResult();
        providerWillAuthoriseForMotoApiPayment();
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseMotoApi(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockEventService, times(1)).emitAndRecordEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getResourceExternalId(), is(charge.getExternalId()));
        assertThat(eventCaptor.getValue().getEventType(), is("PAYMENT_DETAILS_SUBMITTED_BY_API"));
    }

    @Test
    void doAuthoriseMotoApi_shouldIgnoreCorporateCardSurchargeForChargeWithMotoApiAuthorisationMode() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        mockRecordAuthorisationResult();
        providerWillAuthoriseForMotoApiPayment();
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        charge.getGatewayAccount().setCorporateDebitCardSurchargeAmount(50L);

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseMotoApi(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getStatus(), is(CAPTURE_QUEUED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao, times(2)).persistChargeEventOf(eq(charge), isNull());

        assertThat(charge.getCorporateSurcharge().isPresent(), is(false));
    }

    @Test
    void doAuthoriseMotoApi_shouldRespondCaptureQueued_overridingGeneratedTransactionId() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        mockRecordAuthorisationResult();
        providerWillAuthoriseForMotoApiPayment();
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        String generatedTransactionId = "this-will-be-override-to-TRANSACTION-ID-from-provider";

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseMotoApi(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(CAPTURE_QUEUED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getWalletType(), is(nullValue()));
        verify(mockedChargeEventDao, times(2)).persistChargeEventOf(eq(charge), isNull());
    }

    @Test
    void doAuthoriseMotoApi_shouldRetainGeneratedTransactionId_WhenProviderAuthorisationFails() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        String generatedTransactionId = "generated-transaction-id";
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        when(mockedPaymentProvider.authoriseMotoApi(any())).thenThrow(RuntimeException.class);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        CardInformation cardInformation = aCardInformation().build();
        assertThrows(RuntimeException.class, () -> cardAuthorisationService.doAuthoriseMotoApi(charge, cardInformation, authoriseRequest));
        assertThat(charge.getGatewayTransactionId(), is(generatedTransactionId));
    }

    @Test
    void doAuthoriseMotoApi_shouldRespondAuthorisationRejected_whenProviderAuthorisationIsRejected() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        mockRecordAuthorisationResult();
        providerWillRejectForMotoApiPayment();
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseMotoApi(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.REJECTED));

        assertThat(charge.getStatus(), is(AUTHORISATION_REJECTED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
    }

    @Test
    void doAuthoriseMotoApi_shouldRespondAuthorisationCancelled_whenProviderAuthorisationIsCancelled() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockProviderRespondedSuccessfullyResponse(TRANSACTION_ID, AuthoriseStatus.CANCELLED);
        providerWillRespondToAuthoriseMotoApiWith(authResponse);
        mockRecordAuthorisationResult();
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseMotoApi(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AuthoriseStatus.CANCELLED));

        assertThat(charge.getStatus(), is(AUTHORISATION_CANCELLED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
    }

    @Test
    void doAuthoriseMotoApi_shouldRespondAuthorisationError() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");
        mockRecordAuthorisationResult();
        providerWillErrorForMotoApiPayment();
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseMotoApi(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getGatewayError().isPresent());
        assertThat(response.getGatewayError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));

        assertThat(charge.getStatus(), is(AUTHORISATION_ERROR.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(nullValue()));
    }

    @Test
    void doAuthoriseMotoApi_shouldStoreCardDetails_IfAuthorisationRejected() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("424242"), LastDigitsCardNumber.of("4242"),
                "Mr. Pay", CardExpiryDate.valueOf("11/99"), "VISA", CardType.DEBIT, null);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(any())).thenReturn(cardDetailsEntity);
        mockRecordAuthorisationResult();
        providerWillRejectForMotoApiPayment();

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseMotoApi(charge, aCardInformation().build(), authoriseRequest);

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(notNullValue()));
    }

    @Test
    void doAuthoriseMotoApi_shouldStoreCardDetails_ForAuthorisationError() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity(FirstDigitsCardNumber.of("424242"), LastDigitsCardNumber.of("4242"),
                "Mr. Pay", CardExpiryDate.valueOf("11/99"), "VISA", CardType.DEBIT, null);
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(any())).thenReturn(cardDetailsEntity);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        mockRecordAuthorisationResult();
        providerWillErrorForMotoApiPayment();

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseMotoApi(charge, aCardInformation().build(), authoriseRequest);

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(notNullValue()));
    }

    @Test
    void doAuthoriseMotoApi_shouldSetChargeStatusToAuthorisationTimeout_whenGatewayTimedout() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        mockRecordAuthorisationResult();
        providerWillRespondWithErrorForMotoApiPayment(new GatewayException.GatewayConnectionTimeoutException("Connection timed out"));
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseMotoApi(charge, aCardInformation().build(), authoriseRequest);
        assertTrue(response.getGatewayError().isPresent());
        assertThat(response.getGatewayError().get().getErrorType(), is(GATEWAY_CONNECTION_TIMEOUT_ERROR));
        assertThat(charge.getStatus(), is(AUTHORISATION_TIMEOUT.getValue()));
    }

    @Test
    void doAuthoriseMotoApi_shouldThrowAnIllegalStateRuntimeException_whenChargeIsInInvalidStatus() {
        charge.setAuthorisationMode(MOTO_API);
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.UNDEFINED);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        CardInformation cardInformation = aCardInformation().build();
        assertThrows(IllegalStateRuntimeException.class, () -> cardAuthorisationService.doAuthoriseMotoApi(charge, cardInformation, authoriseRequest));

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test
    void doAuthoriseMotoApi_shouldReportUnexpectedError_whenProviderError() throws Exception {
        charge.setAuthorisationMode(MOTO_API);
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", "Mr Test");

        mockRecordAuthorisationResult();
        providerWillRespondWithErrorForMotoApiPayment(new GatewayException.GatewayErrorException("Malformed response received"));
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseMotoApi(charge, aCardInformation().build(), authoriseRequest);

        assertTrue(response.getGatewayError().isPresent());
        assertThat(response.getGatewayError().get().getErrorType(), is(GATEWAY_ERROR));
        assertThat(charge.getStatus(), is(AUTHORISATION_UNEXPECTED_ERROR.getValue()));
    }

    @Test
    void doAuthoriseMotoApi_shouldTransitionToAuthorisationTimeout_andThrowException_whenAuthorisationTimesOut() {
        ChargeEntity charge = createNewChargeWith(1L, CREATED);
        charge.setAuthorisationMode(MOTO_API);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        String cardholderName = "Mr Test";
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", "4242424242424242", "123", "11/99", cardholderName);
        CardInformation cardInformation = aCardInformation().build();
        AuthCardDetails authCardDetails = AuthCardDetails.of(authoriseRequest, charge, cardInformation);

        String generatedTransactionId = "this-will-be-override-to-TRANSACTION-ID-from-provider";

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));
        when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(authCardDetails)).thenReturn(cardDetailsEntity);
        when(mockExecutorService.execute(any(), anyInt())).thenReturn(Pair.of(IN_PROGRESS, null));
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        assertThrows(AuthorisationTimedOutException.class, () -> cardAuthorisationService.doAuthoriseMotoApi(charge, cardInformation, authoriseRequest));

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(cardDetailsEntity));
        assertThat(charge.getStatus(), is(AUTHORISATION_TIMEOUT.getValue()));

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    private void providerWillRespondToAuthoriseWith(GatewayResponse value) throws Exception {
        when(mockedPaymentProvider.authorise(any(), any())).thenReturn(value);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());
    }

    private void providerWillRespondToAuthoriseMotoApiWith(GatewayResponse authResponse) throws GatewayException {
        when(mockedPaymentProvider.authoriseMotoApi(any())).thenReturn(authResponse);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());
    }

    private void providerWillAuthorise() throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockProviderRespondedSuccessfullyResponse(TRANSACTION_ID, AuthoriseStatus.AUTHORISED);
        providerWillRespondToAuthoriseWith(authResponse);
    }

    private void providerWillAuthoriseForMotoApiPayment() throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockProviderRespondedSuccessfullyResponse(TRANSACTION_ID, AuthoriseStatus.AUTHORISED);
        providerWillRespondToAuthoriseMotoApiWith(authResponse);
    }

    private void epdqProviderWillRequire3ds() throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        EpdqAuthorisationResponse epdqResponse = new EpdqAuthorisationResponse();
        epdqResponse.setHtmlAnswer("Base64encodedHtmlForm");
        epdqResponse.setStatus("46");

        GatewayResponseBuilder<EpdqAuthorisationResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse epdq3dsResponse = gatewayResponseBuilder
                .withResponse(epdqResponse)
                .build();
        when(mockedPaymentProvider.authorise(any(), any())).thenReturn(epdq3dsResponse);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(TRANSACTION_ID));
    }

    private void providerWillReject() throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockProviderRespondedSuccessfullyResponse(TRANSACTION_ID, AuthoriseStatus.REJECTED);
        providerWillRespondToAuthoriseWith(authResponse);
    }

    private void providerWillRejectForMotoApiPayment() throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockProviderRespondedSuccessfullyResponse(TRANSACTION_ID, AuthoriseStatus.REJECTED);
        providerWillRespondToAuthoriseMotoApiWith(authResponse);
    }

    private void providerWillError() throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthErrorResponse(AuthoriseStatus.ERROR, "error-code");
        providerWillRespondToAuthoriseWith(authResponse);
    }

    private void providerWillErrorForMotoApiPayment() throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        GatewayResponse authResponse = mockAuthErrorResponse(AuthoriseStatus.ERROR, "error-code");
        providerWillRespondToAuthoriseMotoApiWith(authResponse);
    }

    private void providerWillRespondWithError(Exception gatewayError) throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        when(mockedPaymentProvider.authorise(any(), any())).thenThrow(gatewayError);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());
    }

    private void providerWillRespondWithErrorForMotoApiPayment(Exception gatewayError) throws Exception {
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        when(mockedPaymentProvider.authoriseMotoApi(any())).thenThrow(gatewayError);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());
    }
}
