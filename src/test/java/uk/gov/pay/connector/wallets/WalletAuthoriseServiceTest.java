package uk.gov.pay.connector.wallets;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.amazonaws.util.json.Jackson;
import com.codahale.metrics.Counter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.events.EventQueue;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.logging.AuthorisationLogger;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.paymentprocessor.service.AuthorisationService;
import uk.gov.pay.connector.paymentprocessor.service.CardExecutorService;
import uk.gov.pay.connector.paymentprocessor.service.CardServiceTest;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.WorldpayGooglePayAuthRequest;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.UNDEFINED;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayAuthRequestFixture.anApplePayAuthRequest;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.IN_PROGRESS;

@ExtendWith(MockitoExtension.class)
class WalletAuthoriseServiceTest extends CardServiceTest {

    private static final ProviderSessionIdentifier SESSION_IDENTIFIER = ProviderSessionIdentifier.of("session-identifier");
    private static final String TRANSACTION_ID = "transaction-id";
    
    private static final CardExpiryDate EXPIRY_DATE = CardExpiryDate.valueOf("01/30");
    private static ObjectMapper objectMapper = new ObjectMapper();

    private final ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);

    @Mock
    private CardExecutorService mockExecutorService;

    @Mock
    private Environment mockEnvironment;

    @Mock
    private Counter mockCounter;

    @Mock
    private StateTransitionService mockStateTransitionService;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private EventQueue eventQueue;

    @Mock
    private EmittedEventDao mockEmmittedEventDao;

    @Mock
    protected WalletAuthorisationRequestToAuthCardDetailsConverter mockWalletAuthorisationDataToAuthCardDetailsConverter;

    @Mock
    protected AuthCardDetailsToCardDetailsEntityConverter mockAuthCardDetailsToCardDetailsEntityConverter;

    @Mock
    private RefundService mockRefundService;

    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;

    @Mock
    private TaskQueueService mockTaskQueueService;

    @Mock
    private Worldpay3dsFlexJwtService mockWorldpay3dsFlexJwtService;

    @Mock
    private AuthCardDetails mockAuthCardDetails;

    @Mock
    private CardDetailsEntity mockCardDetailsEntity;

    @Mock
    private AuthorisationConfig mockAuthorisationConfig;

    @Mock
    private IdempotencyDao mockIdempotencyDao;

    @Mock
    private ExternalTransactionStateFactory mockExternalTransactionStateFactory;

    private WalletAuthoriseService walletAuthoriseService;

    private final ApplePayAuthRequest validApplePayDetails =
            anApplePayAuthRequest()
                    .build();

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @InjectMocks
    private EventService mockEventService;

    @InjectMocks
    private PaymentInstrumentService mockPaymentInstrumentService;

    @BeforeEach
    void setUp() {
        lenient().when(mockedProviders.byName(any())).thenReturn(mockedPaymentProvider);
        lenient().when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(TRANSACTION_ID));
        lenient().when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        lenient().when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        lenient().when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        lenient().when(mockWalletAuthorisationDataToAuthCardDetailsConverter.convert(any(WalletAuthorisationRequest.class), nullable(CardExpiryDate.class))).thenReturn(mockAuthCardDetails);
        lenient().when(mockAuthCardDetailsToCardDetailsEntityConverter.convert(mockAuthCardDetails)).thenReturn(mockCardDetailsEntity);
        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();
        ConnectorConfiguration mockConfiguration = mock(ConnectorConfiguration.class);
        lenient().when(mockConfiguration.getEmitPaymentStateTransitionEvents()).thenReturn(true);
        lenient().when(mockConfiguration.getAuthorisationConfig()).thenReturn(mockAuthorisationConfig);
        lenient().when(mockAuthorisationConfig.getAsynchronousAuthTimeoutInMilliseconds()).thenReturn(1000);

        ChargeEventEntity chargeEventEntity = mock(ChargeEventEntity.class);
        AuthorisationService authorisationService = new AuthorisationService(mockExecutorService, mockEnvironment, mockConfiguration);
        ChargeService chargeService = spy(new ChargeService(null, mockedChargeDao, mockedChargeEventDao,
                null, null, null, mockConfiguration, null, mockStateTransitionService,
                ledgerService, mockRefundService, mockEventService, mockPaymentInstrumentService, mockGatewayAccountCredentialsService,
                mockAuthCardDetailsToCardDetailsEntityConverter, mockTaskQueueService, mockWorldpay3dsFlexJwtService, mockIdempotencyDao,
                mockExternalTransactionStateFactory, objectMapper, null));
        walletAuthoriseService = new WalletAuthoriseService(
                mockedProviders,
                chargeService,
                authorisationService,
                mockWalletAuthorisationDataToAuthCardDetailsConverter,
                new AuthorisationLogger(new AuthorisationRequestSummaryStringifier(), new AuthorisationRequestSummaryStructuredLogging()),
                mockEnvironment);

        setUpLogging();
    }

    private void setUpLogging() {
        Logger root = (Logger) LoggerFactory.getLogger(WalletAuthoriseService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    void verifyLogging() throws Exception {
        GatewayResponse gatewayResponse = providerWillAuthorise();
        walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);

        ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(mockAppender, times(4)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents.stream().anyMatch(le -> le.getFormattedMessage().contains(
                format("APPLE_PAY authorisation success - charge_external_id=%s, payment provider response=%s", charge.getExternalId(), gatewayResponse.toString()))
        ), is(true));
    }

    @Test
    void doAuthoriseCard_ApplePay_shouldRespondAuthorisationSuccess() throws Exception {
        providerWillAuthorise();
        ChargeEventEntity chargeEventEntity = mock(ChargeEventEntity.class);
        when(mockedChargeEventDao.persistChargeEventOf(any(), any())).thenReturn(chargeEventEntity);

        GatewayResponse response = walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);

        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getSessionIdentifier().isPresent(), is(true));
        assertThat(response.getSessionIdentifier().get(), is(SESSION_IDENTIFIER));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getCardDetails(), is(mockCardDetailsEntity));
        assertThat(charge.getWalletType(), is(WalletType.APPLE_PAY));
        assertThat(charge.getCorporateSurcharge().isPresent(), is(false));
        assertThat(charge.getEmail(), is(validApplePayDetails.getPaymentInfo().getEmail()));

        verify(mockStateTransitionService).offerPaymentStateTransition(charge.getExternalId(), AUTHORISATION_READY, AUTHORISATION_SUCCESS, chargeEventEntity);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventQueue, times(1)).emitEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getResourceExternalId(), is(charge.getExternalId()));
        assertThat(eventCaptor.getValue().getEventType(), is("PAYMENT_DETAILS_ENTERED"));
    }

    @Test
    void doAuthoriseCard_GooglePay_shouldRespondAuthorisationSuccess() throws Exception {
        providerWillAuthorise();
        ChargeEventEntity chargeEventEntity = mock(ChargeEventEntity.class);
        when(mockedChargeEventDao.persistChargeEventOf(any(), any())).thenReturn(chargeEventEntity);

        WalletAuthorisationRequest authorisationData =
                Jackson.getObjectMapper().readValue(fixture("googlepay/example-auth-request.json"), WorldpayGooglePayAuthRequest.class);

        GatewayResponse<BaseAuthoriseResponse> response = walletAuthoriseService.doAuthorise(charge.getExternalId(), authorisationData);

        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getSessionIdentifier().isPresent(), is(true));
        assertThat(response.getSessionIdentifier().get(), is(SESSION_IDENTIFIER));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getCardDetails(), is(mockCardDetailsEntity));
        assertThat(charge.getWalletType(), is(WalletType.GOOGLE_PAY));
        assertThat(charge.getCorporateSurcharge().isPresent(), is(false));
        assertThat(charge.getEmail(), is(authorisationData.getPaymentInfo().getEmail()));

        verify(mockStateTransitionService).offerPaymentStateTransition(charge.getExternalId(), AUTHORISATION_READY, AUTHORISATION_SUCCESS, chargeEventEntity);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventQueue, times(1)).emitEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getResourceExternalId(), is(charge.getExternalId()));
        assertThat(eventCaptor.getValue().getEventType(), is("PAYMENT_DETAILS_ENTERED"));
    }

    @Test
    void doAuthoriseCard_GooglePay_shouldRespondAuthorisationSuccess_whenCardNumberIsEmptyString() throws Exception {
        providerWillAuthorise();
        ChargeEventEntity chargeEventEntity = mock(ChargeEventEntity.class);
        when(mockedChargeEventDao.persistChargeEventOf(any(), any())).thenReturn(chargeEventEntity);

        WalletAuthorisationRequest authorisationData =
                Jackson.getObjectMapper().readValue(fixture("googlepay/auth-request-with-empty-last-digits-card-number.json"), WorldpayGooglePayAuthRequest.class);

        GatewayResponse<BaseAuthoriseResponse> response = walletAuthoriseService.doAuthorise(charge.getExternalId(), authorisationData);

        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getSessionIdentifier().isPresent(), is(true));
        assertThat(response.getSessionIdentifier().get(), is(SESSION_IDENTIFIER));

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails(), is(nullValue()));
        assertThat(charge.getCardDetails(), is(notNullValue()));
        assertThat(charge.getCardDetails().getFirstDigitsCardNumber(), is(nullValue()));
        assertThat(charge.getCardDetails().getLastDigitsCardNumber(), is(nullValue()));
        assertThat(charge.getWalletType(), is(WalletType.GOOGLE_PAY));
        assertThat(charge.getCorporateSurcharge().isPresent(), is(false));
        assertThat(charge.getEmail(), is(authorisationData.getPaymentInfo().getEmail()));

        verify(mockStateTransitionService).offerPaymentStateTransition(charge.getExternalId(), AUTHORISATION_READY, AUTHORISATION_SUCCESS, chargeEventEntity);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventQueue, times(1)).emitEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getResourceExternalId(), is(charge.getExternalId()));
        assertThat(eventCaptor.getValue().getEventType(), is("PAYMENT_DETAILS_ENTERED"));
    }

    @Test
    void doAuthorise_shouldRespondAuthorisationRejected_whenProviderAuthorisationIsRejected() throws Exception {
        providerWillReject();

        GatewayResponse response = walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);

        assertThat(response.isSuccessful(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_REJECTED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(charge.getWalletType(), is(WalletType.APPLE_PAY));
    }

    @Test
    void doAuthorise_shouldSetProviderTransactionId_whenProviderAuthorisationIsErroredWithoutProviderId() throws Exception {
        providerWillRejectWithNoTransactionId();

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        GatewayResponse response = walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);

        assertThat(response.isSuccessful(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_ERROR.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(charge.getWalletType(), is(WalletType.APPLE_PAY));
    }

    @Test
    void doAuthorise_shouldRespondAuthorisationCancelled_whenProviderAuthorisationIsCancelled() throws Exception {
        GatewayResponse authResponse = mockAuthResponse(TRANSACTION_ID, AuthoriseStatus.CANCELLED, null, null);
        when(mockedPaymentProvider.authoriseWallet(any(WalletAuthorisationGatewayRequest.class))).thenReturn(authResponse);

        GatewayResponse response = walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);

        assertThat(response.isSuccessful(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_CANCELLED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(charge.getWalletType(), is(WalletType.APPLE_PAY));
    }

    @Test
    void shouldRespondAuthorisationError() throws Exception {
        providerWillError();
        GatewayResponse authResponse = mockAuthResponse(null, AuthoriseStatus.ERROR, "error-code", null);
        when(mockedPaymentProvider.authoriseWallet(any(WalletAuthorisationGatewayRequest.class))).thenReturn(authResponse);
        
        
        GatewayResponse response = walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);

        assertThat(response.isFailed(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_ERROR.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(TRANSACTION_ID));
        assertThat(charge.getWalletType(), is(WalletType.APPLE_PAY));
    }

    @Test
    void doAuthorise_shouldStoreCardDetails_evenIfAuthorisationRejected() throws Exception {
        providerWillReject();

        walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(mockCardDetailsEntity));
    }

    @Test
    void doAuthorise_shouldStoreCardDetails_evenIfInAuthorisationError() throws Exception {
        providerWillError();

        walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);

        CardDetailsEntity cardDetails = charge.getCardDetails();
        assertThat(cardDetails, is(mockCardDetailsEntity));
    }

    @Test
    void doAuthorise_shouldStoreProviderSessionId_evenIfAuthorisationRejected() throws Exception {
        providerWillReject();

        walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);

        assertThat(charge.getProviderSessionId(), is(SESSION_IDENTIFIER.toString()));
    }

    @Test
    void doAuthorise_shouldNotStoreProviderSessionId_whenAuthorisationError() throws Exception {
        providerWillError();

        walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);

        assertThat(charge.getProviderSessionId(), is(nullValue()));
    }

    @Test
    void doAuthorise_shouldThrowAnOperationAlreadyInProgressRuntimeException_whenTimeout() {
        when(mockExecutorService.execute(any(), anyInt())).thenReturn(Pair.of(IN_PROGRESS, null));

        try {
            walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);
            fail("Exception not thrown.");
        } catch (OperationAlreadyInProgressRuntimeException e) {
            ErrorResponse response = (ErrorResponse) e.getResponse().getEntity();
            assertThat(response.getMessages(), contains(format("Authorisation for charge already in progress, %s", charge.getExternalId())));
        }
    }

    @Test
    void doAuthorise_shouldThrowAChargeNotFoundRuntimeException_whenChargeDoesNotExist() {
        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";
        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());

        assertThrows(ChargeNotFoundRuntimeException.class, () -> walletAuthoriseService.doAuthorise(chargeId, validApplePayDetails));
    }

    @Test
    void doAuthorise_shouldThrowAnOperationAlreadyInProgressRuntimeException_whenStatusIsAuthorisationReady() {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_READY);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        assertThrows(OperationAlreadyInProgressRuntimeException.class,
                () -> walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails));

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test
    void doAuthorise_shouldThrowAnIllegalStateRuntimeException_whenInvalidStatus() {
        ChargeEntity charge = createNewChargeWith(1L, UNDEFINED);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        assertThrows(IllegalStateRuntimeException.class,
                () -> walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails));

        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test
    void doAuthorise_shouldReportAuthorisationTimeout_whenProviderTimeout() throws Exception {
        providerWillRespondWithError(new GatewayConnectionTimeoutException("Connection timed out"));

        GatewayResponse response = walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);

        assertThat(response.isFailed(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_TIMEOUT.getValue()));
    }

    @Test
    void doAuthorise_shouldReportUnexpectedError_whenProviderError() throws Exception {
        providerWillRespondWithError(new GatewayException.GatewayErrorException("Malformed response received"));

        GatewayResponse response = walletAuthoriseService.doAuthorise(charge.getExternalId(), validApplePayDetails);

        assertThat(response.isFailed(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_UNEXPECTED_ERROR.getValue()));
    }

    private GatewayResponse mockAuthResponse(String TRANSACTION_ID, AuthoriseStatus authoriseStatus, String errorCode, CardExpiryDate cardExpiryDate) {
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        lenient().when(worldpayResponse.getTransactionId()).thenReturn(TRANSACTION_ID);
        lenient().when(worldpayResponse.authoriseStatus()).thenReturn(authoriseStatus);
        lenient().when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        lenient().when(worldpayResponse.getCardExpiryDate()).thenReturn(Optional.ofNullable(cardExpiryDate));
        return responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier(SESSION_IDENTIFIER)
                .build();
    }

    private GatewayResponse mockAuthResponseWithNoTransactionId(AuthoriseStatus authoriseStatus, String errorCode) {
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        when(worldpayResponse.getTransactionId()).thenReturn(null);
        when(worldpayResponse.authoriseStatus()).thenReturn(authoriseStatus);
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        return responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier(SESSION_IDENTIFIER)
                .build();
    }

    void mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue() {
        lenient().doAnswer(invocation -> Pair.of(COMPLETED, ((Supplier) invocation.getArguments()[0]).get()))
                .when(mockExecutorService).execute(any(Supplier.class), anyInt());
    }

    private GatewayResponse providerWillAuthorise() throws Exception {
        GatewayResponse authResponse = mockAuthResponse(TRANSACTION_ID, AuthoriseStatus.AUTHORISED, null, EXPIRY_DATE);
        when(mockedPaymentProvider.authoriseWallet(any(WalletAuthorisationGatewayRequest.class))).thenReturn(authResponse);
        return authResponse;
    }

    private void providerWillReject() throws Exception {
        GatewayResponse authResponse = mockAuthResponse(TRANSACTION_ID, AuthoriseStatus.REJECTED, null, EXPIRY_DATE);
        when(mockedPaymentProvider.authoriseWallet(any(WalletAuthorisationGatewayRequest.class))).thenReturn(authResponse);
    }

    private void providerWillRejectWithNoTransactionId() throws Exception {
        GatewayResponse authResponse = mockAuthResponseWithNoTransactionId(AuthoriseStatus.EXCEPTION, null);
        when(mockedPaymentProvider.authoriseWallet(any(WalletAuthorisationGatewayRequest.class))).thenReturn(authResponse);
    }

    private void providerWillError() throws Exception {
        GatewayResponse authResponse = mockAuthResponse(null, AuthoriseStatus.ERROR, "error-code", null);
        when(mockedPaymentProvider.authoriseWallet(any(WalletAuthorisationGatewayRequest.class))).thenReturn(authResponse);
    }

    private void providerWillRespondWithError(Exception gatewayError) throws Exception {
        when(mockedPaymentProvider.authoriseWallet(any(WalletAuthorisationGatewayRequest.class))).thenThrow(gatewayError);
    }
}
