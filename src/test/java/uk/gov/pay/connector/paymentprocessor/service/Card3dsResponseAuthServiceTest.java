package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.Counter;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ExecutorServiceConfig;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.util.AuthUtils;
import uk.gov.pay.connector.util.XrayUtils;

import java.util.Optional;
import java.util.concurrent.Executors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;

@RunWith(MockitoJUnitRunner.class)
public class Card3dsResponseAuthServiceTest extends CardServiceTest {

    private static final String GENERATED_TRANSACTION_ID = "generated-transaction-id";

    private ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_3DS_REQUIRED, GENERATED_TRANSACTION_ID);
    private Card3dsResponseAuthService card3dsResponseAuthService;

    @Before
    public void setUpCardAuthorisationService() {
        Environment environment = mock(Environment.class);
        Counter mockCounter = mock(Counter.class);
        when(environment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(histogram);

        ConnectorConfiguration config = mock(ConnectorConfiguration.class);
        ExecutorServiceConfig executorService = mock(ExecutorServiceConfig.class);
        when(executorService.getTimeoutInSeconds()).thenReturn(1);
        when(config.getExecutorServiceConfig()).thenReturn(executorService);
        
        ChargeService chargeService = new ChargeService(null, mockedChargeDao, mockedChargeEventDao, null,
                null, config, null);
        CardAuthorisationExecutor cardAuthorisationExecutor = new CardAuthorisationExecutor(config, environment, 
                mock(XrayUtils.class), Executors.newSingleThreadExecutor());

        card3dsResponseAuthService = new Card3dsResponseAuthService(mockedProviders, chargeService, cardAuthorisationExecutor);
    }

    private void setupPaymentProviderMock(String transactionId, AuthoriseStatus authoriseStatus, ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        Gateway3DSAuthorisationResponse authorisationResponse = Gateway3DSAuthorisationResponse.of(authoriseStatus, transactionId);
        when(mockedPaymentProvider.authorise3dsResponse(argumentCaptor.capture())).thenReturn(authorisationResponse);
    }

    @Test
    public void doAuthorise_shouldRespondAuthorisationSuccess() {

        Auth3dsDetails auth3dsDetails = AuthUtils.buildAuth3dsDetails();
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));

        setupPaymentProviderMock(charge.getGatewayTransactionId(), AuthoriseStatus.AUTHORISED, argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        Gateway3DSAuthorisationResponse response = card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), auth3dsDetails);

        assertThat(response.isSuccessful(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        assertTrue(argumentCaptor.getValue().getTransactionId().isPresent());
        assertThat(argumentCaptor.getValue().getTransactionId().get(), is(GENERATED_TRANSACTION_ID));

    }

    @Test
    public void doAuthorise_shouldRetainGeneratedTransactionId_evenIfAuthorisationAborted() {

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.authorise3dsResponse(any())).thenThrow(RuntimeException.class);

        try {
            card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
            fail("Won’t get this far");
        } catch (RuntimeException e) {
            assertThat(charge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        }
    }

    @Test
    public void doAuthorise_shouldPopulateTheProviderSessionId() {

        Auth3dsDetails auth3dsDetails = AuthUtils.buildAuth3dsDetails();
        String providerSessionId = "provider-session-id";
        charge.setProviderSessionId(providerSessionId);

        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));

        setupPaymentProviderMock(charge.getGatewayTransactionId(), AuthoriseStatus.AUTHORISED, argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), auth3dsDetails);

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

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void shouldThrowAChargeNotFoundRuntimeExceptionWhenChargeDoesNotExist() {

        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";

        when(mockedChargeDao.findByExternalId(chargeId)).thenReturn(Optional.empty());

        card3dsResponseAuthService.process3DSecureAuthorisation(chargeId, AuthUtils.buildAuth3dsDetails());
    }

    @Test(expected = OperationAlreadyInProgressRuntimeException.class)
    public void shouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenStatusIsAuthorisation3dsReady() {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_3DS_READY);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void shouldThrowAnIllegalStateRuntimeExceptionWhenInvalidStatus() {

        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_READY);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void shouldThrowChargeExpiredRuntimeExceptionWhenChargeExpired() {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.EXPIRED);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
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

        setupPaymentProviderMock(transactionId, authoriseStatus, argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        return card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
    }

    private Gateway3DSAuthorisationResponse anAuthorisationErrorResponse(ChargeEntity charge,
                                                                         ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        setupPaymentProviderMock(null, AuthoriseStatus.REJECTED, argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        return card3dsResponseAuthService.process3DSecureAuthorisation(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
    }
}
