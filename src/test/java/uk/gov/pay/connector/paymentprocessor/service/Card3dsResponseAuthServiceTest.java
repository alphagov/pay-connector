package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.Counter;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.Authorisation3dsConfig;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.Worldpay3dsFlexRequiredParams;
import uk.gov.pay.connector.gateway.worldpay.Worldpay3dsRequiredParams;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.util.AuthUtils;

import java.util.Optional;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.IN_PROGRESS;

@RunWith(MockitoJUnitRunner.class)
public class Card3dsResponseAuthServiceTest extends CardServiceTest {

    @Mock
    private LedgerService ledgerService;
    @Mock
    private CardExecutorService mockExecutorService;
    @Mock
    private EventService mockEventService;
    @Mock
    private StateTransitionService mockStateTransitionService;
    @Mock
    private Authorisation3dsConfig mockAuthorisation3dsConfig;
    @Mock
    private NorthAmericanRegionMapper northAmericanRegionMapper;
    @Mock
    private RefundService mockedRefundService;
    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;
    @Mock
    private PaymentProviders mockPaymentProviders;
    @Mock
    private StripePaymentProvider mockStripePaymentProvider;

    private static final String GENERATED_TRANSACTION_ID = "generated-transaction-id";

    private static final String REQUIRES_3DS_ISSUER_URL = "https://www.cardissuer.test/3ds?id=12345";
    private static final String REQUIRES_3DS_PA_REQUEST = "pa-request";

    private static final String REQUIRES_3DS_WORLDPAY_3DS_FLEX_CHALLENGE_ACS_URL = "https://www.cardissuer.test/3ds2?id=54321";
    private static final String REQUIRES_3DS_WORLDPAY_3DS_FLEX_CHALLENGE_TRANSACTION_ID = "challenge-transaction-id";
    private static final String REQUIRES_3DS_WORLDPAY_3DS_FLEX_CHALLENGE_PAYLOAD = "challenge-payload";
    private static final String REQUIRES_3DS_WORLDPAY_3DS_FLEX_3DS_VERSION = "2";

    private ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_3DS_REQUIRED, GENERATED_TRANSACTION_ID);
    private ChargeService chargeService;
    private Card3dsResponseAuthService card3dsResponseAuthService;

    @Before
    public void setUpCardAuthorisationService() {
        Environment mockEnvironment = mock(Environment.class);
        Counter mockCounter = mock(Counter.class);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
//        when(mockPaymentProviders.byName(PaymentGatewayName.STRIPE)).thenReturn(mockStripePaymentProvider);

        ConnectorConfiguration mockConfiguration = mock(ConnectorConfiguration.class);
        when(mockConfiguration.getAuthorisation3dsConfig()).thenReturn(mockAuthorisation3dsConfig);

        chargeService = new ChargeService(null, mockedChargeDao, mockedChargeEventDao, null,
                null, mockConfiguration, mockPaymentProviders, mockStateTransitionService, ledgerService,
                mockedRefundService, mockEventService, mockGatewayAccountCredentialsService, northAmericanRegionMapper);
        AuthorisationService authorisationService = new AuthorisationService(mockExecutorService, mockEnvironment);

        card3dsResponseAuthService = new Card3dsResponseAuthService(mockedProviders, chargeService, authorisationService, mockConfiguration);
    }

    public void setupMockExecutorServiceMock() {
        doAnswer(invocation -> Pair.of(COMPLETED, ((Supplier) invocation.getArguments()[0]).get()))
                .when(mockExecutorService).execute(any(Supplier.class));
    }

    private void setupPaymentProviderMock(String transactionId, AuthoriseStatus authoriseStatus, Gateway3dsRequiredParams gateway3dsRequiredParams,
                                          ProviderSessionIdentifier providerSessionIdentifier,
                                          ArgumentCaptor<Auth3dsResponseGatewayRequest> auth3dsResponseGatewayRequestArgumentCaptor) {
        Gateway3DSAuthorisationResponse authorisationResponse = Gateway3DSAuthorisationResponse.of(authoriseStatus, transactionId, gateway3dsRequiredParams,
                providerSessionIdentifier);
        when(mockedPaymentProvider.authorise3dsResponse(auth3dsResponseGatewayRequestArgumentCaptor.capture())).thenReturn(authorisationResponse);
    }

    @Test
    public void process3DSecureAuthorisation_shouldRespondAuthorisationSuccess() {

        Auth3dsResult auth3dsResult = AuthUtils.buildAuth3dsResult();
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(charge.getGatewayTransactionId(), AuthoriseStatus.AUTHORISED, null, null,
                argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        Gateway3DSAuthorisationResponse response = card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), auth3dsResult);

        assertThat(response.isSuccessful(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        assertTrue(argumentCaptor.getValue().getTransactionId().isPresent());
        assertThat(argumentCaptor.getValue().getTransactionId().get(), is(GENERATED_TRANSACTION_ID));

    }

    @Test
    public void process3DSecureAuthorisation_shouldRetainGeneratedTransactionId_evenIfAuthorisationAborted() {

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.authorise3dsResponse(any())).thenThrow(RuntimeException.class);

        setupMockExecutorServiceMock();

        try {
            card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsResult());
            fail("Won’t get this far");
        } catch (RuntimeException e) {
            assertThat(charge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        }
    }

    @Test
    public void process3DSecureAuthorisation_shouldPopulateTheProviderSessionId() {

        Auth3dsResult auth3dsResult = AuthUtils.buildAuth3dsResult();
        ProviderSessionIdentifier providerSessionId = ProviderSessionIdentifier.of("provider-session-id");
        charge.setProviderSessionId(providerSessionId.toString());

        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(charge.getGatewayTransactionId(), AuthoriseStatus.AUTHORISED, null, null, argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), auth3dsResult);

        assertTrue(argumentCaptor.getValue().getProviderSessionId().isPresent());
        assertThat(argumentCaptor.getValue().getProviderSessionId().get(), is(providerSessionId));
    }

    @Test
    public void shouldRespondAuthorisationRejected() {
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_3DS_REQUIRED, GENERATED_TRANSACTION_ID);
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        Gateway3DSAuthorisationResponse response = anAuthorisationRejectedResponse(charge, charge.getGatewayTransactionId(), argumentCaptor);

        assertThat(response.isSuccessful(), is(false));

        assertThat(charge.getStatus(), is(AUTHORISATION_REJECTED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        assertTrue(argumentCaptor.getValue().getTransactionId().isPresent());
        assertThat(argumentCaptor.getValue().getTransactionId().get(), is(GENERATED_TRANSACTION_ID));
    }

    @Test
    public void shouldRespondAuthorisationCancelled() {
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_3DS_REQUIRED, GENERATED_TRANSACTION_ID);
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        Gateway3DSAuthorisationResponse response = anAuthorisationCancelledResponse(charge, charge.getGatewayTransactionId(), argumentCaptor);

        assertThat(response.isSuccessful(), is(false));
        assertThat(charge.getStatus(), is(AUTHORISATION_CANCELLED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        assertTrue(argumentCaptor.getValue().getTransactionId().isPresent());
        assertThat(argumentCaptor.getValue().getTransactionId().get(), is(GENERATED_TRANSACTION_ID));
    }

    @Test
    public void shouldRespondAuthorisationError() {
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);
        Gateway3DSAuthorisationResponse response = anAuthorisationErrorResponse(charge, argumentCaptor);

        assertThat(response.isSuccessful(), is(false));
    }

    @Test
    public void process3DSecureAuthorisation_shouldStoreWorldpay3dsRequiredParams() {
        Auth3dsResult auth3dsResult = AuthUtils.buildAuth3dsResult();
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockAuthorisation3dsConfig.getMaximumNumberOfTimesToAllowUserToAttempt3ds()).thenReturn(3);

        Gateway3dsRequiredParams gateway3dsRequiredParams = new Worldpay3dsRequiredParams(REQUIRES_3DS_ISSUER_URL, REQUIRES_3DS_PA_REQUEST);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(charge.getGatewayTransactionId(), AuthoriseStatus.REQUIRES_3DS, gateway3dsRequiredParams,
                ProviderSessionIdentifier.of("provider-session-identifier"),
                argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        Gateway3DSAuthorisationResponse response = card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), auth3dsResult);

        assertThat(response.isSuccessful(), is(false));
        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        assertThat(charge.get3dsRequiredDetails().getIssuerUrl(), is(REQUIRES_3DS_ISSUER_URL));
        assertThat(charge.get3dsRequiredDetails().getPaRequest(), is(REQUIRES_3DS_PA_REQUEST));
        assertThat(charge.getProviderSessionId(), is("provider-session-identifier"));

        assertTrue(argumentCaptor.getValue().getTransactionId().isPresent());
        assertThat(argumentCaptor.getValue().getTransactionId().get(), is(GENERATED_TRANSACTION_ID));
    }

    @Test
    public void process3DSecureAuthorisation_shouldStoreWorldpay3dsFlexRequiredParams() {
        Auth3dsResult auth3dsResult = AuthUtils.buildAuth3dsResult();
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockAuthorisation3dsConfig.getMaximumNumberOfTimesToAllowUserToAttempt3ds()).thenReturn(3);

        Gateway3dsRequiredParams gateway3dsRequiredParams = new Worldpay3dsFlexRequiredParams(REQUIRES_3DS_WORLDPAY_3DS_FLEX_CHALLENGE_ACS_URL,
                REQUIRES_3DS_WORLDPAY_3DS_FLEX_CHALLENGE_TRANSACTION_ID, REQUIRES_3DS_WORLDPAY_3DS_FLEX_CHALLENGE_PAYLOAD,
                REQUIRES_3DS_WORLDPAY_3DS_FLEX_3DS_VERSION);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(charge.getGatewayTransactionId(), AuthoriseStatus.REQUIRES_3DS, gateway3dsRequiredParams,
                ProviderSessionIdentifier.of("provider-session-identifier"), argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        Gateway3DSAuthorisationResponse response = card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), auth3dsResult);

        assertThat(response.isSuccessful(), is(false));
        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        assertThat(charge.get3dsRequiredDetails().getWorldpayChallengeAcsUrl(), is(REQUIRES_3DS_WORLDPAY_3DS_FLEX_CHALLENGE_ACS_URL));
        assertThat(charge.get3dsRequiredDetails().getWorldpayChallengeTransactionId(), is(REQUIRES_3DS_WORLDPAY_3DS_FLEX_CHALLENGE_TRANSACTION_ID));
        assertThat(charge.get3dsRequiredDetails().getWorldpayChallengePayload(), is(REQUIRES_3DS_WORLDPAY_3DS_FLEX_CHALLENGE_PAYLOAD));
        assertThat(charge.get3dsRequiredDetails().getThreeDsVersion(), is(REQUIRES_3DS_WORLDPAY_3DS_FLEX_3DS_VERSION));
        assertThat(charge.getProviderSessionId(), is("provider-session-identifier"));

        assertTrue(argumentCaptor.getValue().getTransactionId().isPresent());
        assertThat(argumentCaptor.getValue().getTransactionId().get(), is(GENERATED_TRANSACTION_ID));
    }

    @Test
    public void process3DSecureAuthorisation_shouldSetStatusToAuthorisation3dsRequiredIfMaxAttemptsNotExceeded() {
        Auth3dsResult auth3dsResult = AuthUtils.buildAuth3dsResult();
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockAuthorisation3dsConfig.getMaximumNumberOfTimesToAllowUserToAttempt3ds()).thenReturn(3);
        when(mockedChargeDao.count3dsRequiredEventsForChargeExternalId(charge.getExternalId())).thenReturn(2);

        Gateway3dsRequiredParams gateway3dsRequiredParams = new Worldpay3dsRequiredParams(REQUIRES_3DS_ISSUER_URL, REQUIRES_3DS_PA_REQUEST);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(charge.getGatewayTransactionId(), AuthoriseStatus.REQUIRES_3DS, gateway3dsRequiredParams,
                ProviderSessionIdentifier.of("provider-session-identifier"), argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        Gateway3DSAuthorisationResponse response = card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), auth3dsResult);

        assertThat(response.isSuccessful(), is(false));
        assertThat(charge.getStatus(), is(AUTHORISATION_3DS_REQUIRED.getValue()));
    }

    @Test
    public void process3DSecureAuthorisation_shouldSetStatusToAuthorisationRejectedIfMaxAttemptsExceeded() {
        Auth3dsResult auth3dsResult = AuthUtils.buildAuth3dsResult();
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockAuthorisation3dsConfig.getMaximumNumberOfTimesToAllowUserToAttempt3ds()).thenReturn(3);
        when(mockedChargeDao.count3dsRequiredEventsForChargeExternalId(charge.getExternalId())).thenReturn(3);

        Gateway3dsRequiredParams gateway3dsRequiredParams = new Worldpay3dsRequiredParams(REQUIRES_3DS_ISSUER_URL, REQUIRES_3DS_PA_REQUEST);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(charge.getGatewayTransactionId(), AuthoriseStatus.REQUIRES_3DS, gateway3dsRequiredParams,
                ProviderSessionIdentifier.of("provider-session-identifier"), argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        Gateway3DSAuthorisationResponse response = card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), auth3dsResult);

        assertThat(response.isSuccessful(), is(false));
        assertThat(charge.getStatus(), is(AUTHORISATION_REJECTED.getValue()));
    }
    
    @Test
    public void authoriseShouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenTimeout() {
        when(mockExecutorService.execute(any())).thenReturn(Pair.of(IN_PROGRESS, null));

        try {
            card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsResult());
            fail("Exception not thrown.");
        } catch (OperationAlreadyInProgressRuntimeException e) {
            ErrorResponse response = (ErrorResponse) e.getResponse().getEntity();
            assertThat(response.getMessages(), contains(format("Authorisation for charge already in progress, %s", charge.getExternalId())));
        }
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void shouldThrowAChargeNotFoundRuntimeExceptionWhenChargeDoesNotExist() {

        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";

        setupMockExecutorServiceMock();
        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());

        card3dsResponseAuthService.process3DSecureAuthorisation(chargeId, AuthUtils.buildAuth3dsResult());
    }

    @Test(expected = OperationAlreadyInProgressRuntimeException.class)
    public void shouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenStatusIsAuthorisation3dsReady() {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_3DS_READY);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();

        card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsResult());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void shouldThrowAnIllegalStateRuntimeExceptionWhenInvalidStatus() {

        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_READY);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();

        card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsResult());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void shouldThrowChargeExpiredRuntimeExceptionWhenChargeExpired() {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.EXPIRED);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();

        card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsResult());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    private Gateway3DSAuthorisationResponse anAuthorisationRejectedResponse(ChargeEntity charge,
                                                                            String transactionId,
                                                                            ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        return anAuthorisedFailedResponse(charge, transactionId, AuthoriseStatus.REJECTED, argumentCaptor);
    }

    private Gateway3DSAuthorisationResponse anAuthorisationCancelledResponse(ChargeEntity charge,
                                                                             String transactionId,
                                                                             ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        return anAuthorisedFailedResponse(charge, transactionId, AuthoriseStatus.CANCELLED, argumentCaptor);
    }

    private Gateway3DSAuthorisationResponse anAuthorisedFailedResponse(ChargeEntity charge,
                                                                       String transactionId, AuthoriseStatus authoriseStatus,
                                                                       ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(transactionId, authoriseStatus, null, null, argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        return card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsResult());
    }

    private Gateway3DSAuthorisationResponse anAuthorisationErrorResponse(ChargeEntity charge,
                                                                         ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(null, AuthoriseStatus.REJECTED, null, null, argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        return card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsResult());
    }
}
