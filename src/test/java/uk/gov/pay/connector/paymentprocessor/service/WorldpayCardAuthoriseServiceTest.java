package uk.gov.pay.connector.paymentprocessor.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.codahale.metrics.Counter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.core.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeEligibleForCaptureService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.charge.util.PaymentInstrumentEntityToAuthCardDetailsConverter;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.GatewayDoesNotRequire3dsAuthorisation;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gateway.worldpay.WorldpayAuthorisationRequestSummary;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.logging.AuthorisationLogger;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.paymentprocessor.api.AuthorisationResponse;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.function.Supplier;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gateway.util.XMLUnmarshaller.unmarshall;
import static uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity.Worldpay3dsFlexCredentialsEntityBuilder.aWorldpay3dsFlexCredentialsEntity;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_3DS_FLEX_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_EXEMPTION_REQUEST_DECLINE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_EXEMPTION_REQUEST_HONOURED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class WorldpayCardAuthoriseServiceTest extends CardServiceTest {

    private static final ProviderSessionIdentifier SESSION_IDENTIFIER = ProviderSessionIdentifier.of("session-identifier");
    private static final String TRANSACTION_ID = "transaction-id";
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CardExecutorService mockExecutorService;

    @Mock
    private PaymentProvider mockedWorldpayPaymentProvider;

    @Mock
    private TaskQueueService mockTaskQueueService;

    @Mock
    private Worldpay3dsFlexJwtService mockWorldpay3dsFlexJwtService;
    
    @Mock
    ChargeEligibleForCaptureService mockChargeEligibleForCaptureService;

    @Mock
    private Appender<ILoggingEvent> mockAppender;
    
    @Mock
    private ConnectorConfiguration mockConfiguration;
    
    @Mock
    private AuthorisationConfig mockAuthorisationConfig;
    
    @Mock
    private EventService mockEventService;

    private final InstantSource fixedInstantSource = InstantSource.fixed(Instant.parse("2024-11-11T10:07:00Z"));

    private AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
    private CardAuthoriseService cardAuthorisationService;
    private ChargeEntity charge;
    private GatewayAccountEntity gatewayAccount;

    @BeforeEach
    void setup() {
        Environment environment = mock(Environment.class);

        when(environment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mock(Counter.class));

        charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
        charge.setPaymentProvider("worldpay");
        gatewayAccount = charge.getGatewayAccount();
        gatewayAccount.setRequires3ds(true);
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        ChargeService chargeService = new ChargeService(null, mockedChargeDao, mockedChargeEventDao,
                null, mock(AgreementDao.class), null, mock(ConnectorConfiguration.class), null,
                mock(StateTransitionService.class), mock(LedgerService.class), mock(RefundService.class),
                mockEventService, mock(PaymentInstrumentService.class), mock(GatewayAccountCredentialsService.class),
                mock(AuthCardDetailsToCardDetailsEntityConverter.class), mockTaskQueueService, mockWorldpay3dsFlexJwtService, mock(IdempotencyDao.class),
                mock(ExternalTransactionStateFactory.class), objectMapper, null, fixedInstantSource);

        when(mockConfiguration.getAuthorisationConfig()).thenReturn(mockAuthorisationConfig);
        when(mockAuthorisationConfig.getAsynchronousAuthTimeoutInMilliseconds()).thenReturn(1000);
        
        cardAuthorisationService = new CardAuthoriseService(
                mockedCardTypeDao,
                mockedProviders,
                new AuthorisationService(mockExecutorService, environment, mockConfiguration),
                chargeService,
                new AuthorisationLogger(new AuthorisationRequestSummaryStringifier(), new AuthorisationRequestSummaryStructuredLogging()),
                mockChargeEligibleForCaptureService, mock(PaymentInstrumentEntityToAuthCardDetailsConverter.class),
                environment);

        mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue();

        when(mockedProviders.byName(WORLDPAY)).thenReturn(mockedWorldpayPaymentProvider);
        when(mockedWorldpayPaymentProvider.generateTransactionId()).thenReturn(Optional.of(TRANSACTION_ID));
        when(mockedWorldpayPaymentProvider.generateAuthorisationRequestSummary(gatewayAccount, authCardDetails, false))
                .thenReturn(new WorldpayAuthorisationRequestSummary(gatewayAccount, authCardDetails, false));

        Logger root = (Logger) LoggerFactory.getLogger(CardAuthoriseService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    void authorise_with_exemption_when_exemption_honoured_but_authorisation_refused_results_in_rejected() throws Exception {
        worldpayRespondsWith(null, load(WORLDPAY_EXEMPTION_REQUEST_DECLINE_RESPONSE));

        var worldpay3dsFlexCredentialsEntity = aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build();
        gatewayAccount.setWorldpay3dsFlexCredentialsEntity(worldpay3dsFlexCredentialsEntity);
        when(mockedWorldpayPaymentProvider.generateAuthorisationRequestSummary(gatewayAccount, authCardDetails, false))
                .thenReturn(new WorldpayAuthorisationRequestSummary(gatewayAccount, authCardDetails, false));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseWeb(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(BaseAuthoriseResponse.AuthoriseStatus.REJECTED));
        assertThat(charge.getStatus(), is(AUTHORISATION_REJECTED.getValue()));
        assertThat(charge.getRequires3ds(), is(false));

        ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        String log = loggingEventArgumentCaptor.getAllValues().get(0).getMessage();
        assertTrue(log.contains("Authorisation with billing address and with 3DS data and without device data collection result"));
        assertTrue(log.contains("Worldpay authorisation response (orderCode: transaction-id, lastEvent: REFUSED, exemptionResponse result: HONOURED, exemptionResponse reason: HIGH_RISK)"));

        verifyGatewayDoesNotRequire3dsEventWasEmitted(charge);
    }

    @Test
    void authorise_with_exemption_flag_results_in_issuer_honouring_exemption_request() throws Exception {
        worldpayRespondsWith(null, load(WORLDPAY_EXEMPTION_REQUEST_HONOURED_RESPONSE));

        var worldpay3dsFlexCredentialsEntity = aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build();
        gatewayAccount.setWorldpay3dsFlexCredentialsEntity(worldpay3dsFlexCredentialsEntity);
        when(mockedWorldpayPaymentProvider.generateAuthorisationRequestSummary(gatewayAccount, authCardDetails, false))
                .thenReturn(new WorldpayAuthorisationRequestSummary(gatewayAccount, authCardDetails, false));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseWeb(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getRequires3ds(), is(false));

        ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        String log = loggingEventArgumentCaptor.getAllValues().get(0).getMessage();
        assertTrue(log.contains("Authorisation with billing address and with 3DS data and without device data collection result"));
        assertTrue(log.contains("Worldpay authorisation response (orderCode: transaction-id, lastEvent: AUTHORISED, exemptionResponse result: HONOURED, exemptionResponse reason: ISSUER_HONOURED)"));

        verifyGatewayDoesNotRequire3dsEventWasEmitted(charge);
    }

    @Test
    void authorise_with_exemption_when_3ds_challenge_required_results_in_authorisation_3ds_required() throws Exception {
        worldpayRespondsWith(null, load(WORLDPAY_3DS_FLEX_RESPONSE));

        var worldpay3dsFlexCredentialsEntity = aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build();
        gatewayAccount.setWorldpay3dsFlexCredentialsEntity(worldpay3dsFlexCredentialsEntity);

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseWeb(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));
        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        assertThat(charge.getRequires3ds(), is(true));

        verify(mockEventService, never()).emitAndRecordEvent(any(GatewayDoesNotRequire3dsAuthorisation.class));
    }

    @Test
    void authorise_with_exemption_when_3ds_requested_results_in_authorisation_3ds_required() throws Exception {
        worldpayRespondsWith(null, load(WORLDPAY_3DS_RESPONSE));

        var worldpay3dsFlexCredentialsEntity = aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build();
        gatewayAccount.setWorldpay3dsFlexCredentialsEntity(worldpay3dsFlexCredentialsEntity);

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseWeb(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));
        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        assertThat(charge.getRequires3ds(), is(true));

        verify(mockEventService, never()).emitAndRecordEvent(any(GatewayDoesNotRequire3dsAuthorisation.class));
    }

    @Test
    void do_authorise_should_respond_with_3ds_response_for_3ds_orders() throws Exception {
        var worldpayOrderStatusResponse = worldpayRespondsWith(null, load(WORLDPAY_3DS_RESPONSE));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseWeb(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));

        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails().getIssuerUrl(), is(worldpayOrderStatusResponse.getIssuerUrl()));
        assertThat(charge.get3dsRequiredDetails().getPaRequest(), is(worldpayOrderStatusResponse.getPaRequest()));

        verify(mockEventService, never()).emitAndRecordEvent(any(GatewayDoesNotRequire3dsAuthorisation.class));
    }

    @Test
    void do_authorise_should_respond_with_3ds_response_for_3ds_flex_orders() throws Exception {
        var worldpayOrderStatusResponse = worldpayRespondsWith(SESSION_IDENTIFIER, load(WORLDPAY_3DS_FLEX_RESPONSE));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseWeb(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));

        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails().getWorldpayChallengeAcsUrl(), is(worldpayOrderStatusResponse.getChallengeAcsUrl()));
        assertThat(charge.get3dsRequiredDetails().getWorldpayChallengeTransactionId(), is(worldpayOrderStatusResponse.getChallengeTransactionId()));
        assertThat(charge.get3dsRequiredDetails().getWorldpayChallengePayload(), is(worldpayOrderStatusResponse.getChallengePayload()));
        assertThat(charge.get3dsRequiredDetails().getThreeDsVersion(), is(worldpayOrderStatusResponse.getThreeDsVersion()));
        assertThat(charge.getRequires3ds(), is(true));

        verify(mockEventService, never()).emitAndRecordEvent(any(GatewayDoesNotRequire3dsAuthorisation.class));
    }

    @Test
    void do_authorise_should_respond_with_3ds_response_for_3ds_orders_with_worldpay_machine_cookie() throws Exception {
        var worldpayOrderStatusResponse = worldpayRespondsWith(SESSION_IDENTIFIER, load(WORLDPAY_3DS_RESPONSE));

        AuthorisationResponse response = cardAuthorisationService.doAuthoriseWeb(charge.getExternalId(), authCardDetails);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));

        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        verify(mockedChargeEventDao).persistChargeEventOf(eq(charge), isNull());
        assertThat(charge.get3dsRequiredDetails().getIssuerUrl(), is(worldpayOrderStatusResponse.getIssuerUrl()));
        assertThat(charge.get3dsRequiredDetails().getPaRequest(), is(worldpayOrderStatusResponse.getPaRequest()));
        assertThat(charge.getRequires3ds(), is(true));

        verify(mockEventService, never()).emitAndRecordEvent(any(GatewayDoesNotRequire3dsAuthorisation.class));
    }

    private void verifyGatewayDoesNotRequire3dsEventWasEmitted(ChargeEntity chargeEntity) {
        GatewayDoesNotRequire3dsAuthorisation event = GatewayDoesNotRequire3dsAuthorisation.from(chargeEntity, fixedInstantSource.instant());
        verify(mockEventService).emitAndRecordEvent(event);
    }

    private WorldpayOrderStatusResponse worldpayRespondsWith(ProviderSessionIdentifier sessionIdentifier,
                                                             String worldpayXmlResponse) throws Exception {

        var worldpayOrderStatusResponse = unmarshall(worldpayXmlResponse, WorldpayOrderStatusResponse.class);
        GatewayResponse<WorldpayOrderStatusResponse> responseBuilder = responseBuilder()
                .withResponse(worldpayOrderStatusResponse)
                .withSessionIdentifier(sessionIdentifier).build();
        when(mockedWorldpayPaymentProvider.authorise(any(), any())).thenReturn(responseBuilder);
        return worldpayOrderStatusResponse;
    }

    private void mockExecutorServiceWillReturnCompletedResultWithSupplierReturnValue() {
        doAnswer(invocation -> Pair.of(COMPLETED, ((Supplier) invocation.getArguments()[0]).get()))
                .when(mockExecutorService).execute(any(Supplier.class), anyInt());
    }
}
