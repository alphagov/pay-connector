package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.hamcrest.HamcrestArgumentMatcher;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.exception.ChargeExpiredRuntimeException;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.util.AuthUtils;

import javax.persistence.OptimisticLockException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.IN_PROGRESS;

@RunWith(MockitoJUnitRunner.class)
public class Card3dsResponseAuthServiceTest extends CardServiceTest {

    private static final String GENERATED_TRANSACTION_ID = "generated-transaction-id";

    private ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_3DS_REQUIRED, GENERATED_TRANSACTION_ID);
    private ChargeService chargeService;
    private Card3dsResponseAuthService card3dsResponseAuthService;
    private CardExecutorService mockExecutorService = mock(CardExecutorService.class);

    @Before
    public void setUpCardAuthorisationService() {
        Environment mockEnvironment = mock(Environment.class);
        mockMetricRegistry = mock(MetricRegistry.class);
        Counter mockCounter = mock(Counter.class);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        ConnectorConfiguration mockConfiguration = mock(ConnectorConfiguration.class);
        chargeService = new ChargeService(null, mockedChargeDao, mockedChargeEventDao, null,
                null, mockConfiguration, null);
        card3dsResponseAuthService = new Card3dsResponseAuthService(mockedChargeDao, mockedChargeEventDao,
                mockedProviders, mockExecutorService, chargeService, mockEnvironment);
    }

    public void setupMockExecutorServiceMock() {
        doAnswer(invocation -> Pair.of(COMPLETED, ((Supplier) invocation.getArguments()[0]).get()))
                .when(mockExecutorService).execute(any(Supplier.class));
    }

    private void setupPaymentProviderMock(String transactionId, AuthoriseStatus authoriseStatus, String errorCode, ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        when(worldpayResponse.getTransactionId()).thenReturn(transactionId);
        when(worldpayResponse.authoriseStatus()).thenReturn(authoriseStatus);
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        GatewayResponseBuilder<WorldpayOrderStatusResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse authorisationResponse = gatewayResponseBuilder
                .withResponse(worldpayResponse)
                .build();
        when(mockedPaymentProvider.authorise3dsResponse(argumentCaptor.capture())).thenReturn(authorisationResponse);
    }

    @Test
    public void doAuthorise_shouldRespondAuthorisationSuccess() throws Exception {

        Auth3dsDetails auth3dsDetails = AuthUtils.buildAuth3dsDetails();
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(charge.getGatewayTransactionId(), AuthoriseStatus.AUTHORISED, null, argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        GatewayResponse response = card3dsResponseAuthService.doAuthorise(charge.getExternalId(), auth3dsDetails);

        assertThat(response.isSuccessful(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        assertTrue(argumentCaptor.getValue().getTransactionId().isPresent());
        assertThat(argumentCaptor.getValue().getTransactionId().get(), is(GENERATED_TRANSACTION_ID));

    }

    @Test
    public void doAuthorise_shouldRetainGeneratedTransactionId_evenIfAuthorisationAborted() throws Exception {

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.authorise3dsResponse(any())).thenThrow(RuntimeException.class);

        setupMockExecutorServiceMock();

        try {
            card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
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

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(charge.getGatewayTransactionId(), AuthoriseStatus.AUTHORISED, null, argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        card3dsResponseAuthService.doAuthorise(charge.getExternalId(), auth3dsDetails);

        assertTrue(argumentCaptor.getValue().getProviderSessionId().isPresent());
        assertThat(argumentCaptor.getValue().getProviderSessionId().get(), is(providerSessionId));
    }

    @Test
    public void shouldRespondAuthorisationRejected() throws Exception {
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_3DS_REQUIRED, GENERATED_TRANSACTION_ID);
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        GatewayResponse response = anAuthorisationRejectedResponse(charge, charge.getGatewayTransactionId(), argumentCaptor);

        assertThat(response.isSuccessful(), is(true));

        assertThat(charge.getStatus(), is(AUTHORISATION_REJECTED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        assertTrue(argumentCaptor.getValue().getTransactionId().isPresent());
        assertThat(argumentCaptor.getValue().getTransactionId().get(), is(GENERATED_TRANSACTION_ID));
    }

    @Test
    public void shouldRespondAuthorisationCancelled() throws Exception {
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_3DS_REQUIRED, GENERATED_TRANSACTION_ID);
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        GatewayResponse response = anAuthorisationCancelledResponse(charge, charge.getGatewayTransactionId(), argumentCaptor);

        assertThat(response.isSuccessful(), is(true));
        assertThat(charge.getStatus(), is(AUTHORISATION_CANCELLED.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        assertTrue(argumentCaptor.getValue().getTransactionId().isPresent());
        assertThat(argumentCaptor.getValue().getTransactionId().get(), is(GENERATED_TRANSACTION_ID));
    }

    @Test
    public void shouldRespondAuthorisationError() throws Exception {
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);
        GatewayResponse response = anAuthorisationErrorResponse(charge, argumentCaptor);

        assertThat(response.isFailed(), is(true));
        //  assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_ERROR.toString()));
        //  assertThat(reloadedCharge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
    }

    @Test
    public void authoriseShouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenTimeout() throws Exception {
        when(mockExecutorService.execute(any())).thenReturn(Pair.of(IN_PROGRESS, null));

        try {
            card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
            fail("Exception not thrown.");
        } catch (OperationAlreadyInProgressRuntimeException e) {
            Map<String, String> expectedMessage = ImmutableMap.of("message", format("Authorisation for charge already in progress, %s", charge.getExternalId()));
            assertThat(e.getResponse().getEntity(), is(expectedMessage));
        }
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void shouldThrowAChargeNotFoundRuntimeExceptionWhenChargeDoesNotExist() {

        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";

        setupMockExecutorServiceMock();
        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());

        card3dsResponseAuthService.doAuthorise(chargeId, AuthUtils.buildAuth3dsDetails());
    }

    @Test(expected = OperationAlreadyInProgressRuntimeException.class)
    public void shouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenStatusIsAuthorisation3dsReady() {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_3DS_READY);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();

        card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void shouldThrowAnIllegalStateRuntimeExceptionWhenInvalidStatus() throws Exception {

        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_READY);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();

        card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }
    
    @Test(expected = IllegalStateRuntimeException.class)
    public void shouldThrowChargeExpiredRuntimeExceptionWhenChargeExpired() {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.EXPIRED);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();

        card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    private GatewayResponse anAuthorisationRejectedResponse(ChargeEntity charge,
                                                            String transactionId,
                                                            ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        return anAuthorisedFailedResponse(charge, transactionId, AuthoriseStatus.REJECTED, argumentCaptor);
    }

    private GatewayResponse anAuthorisationCancelledResponse(ChargeEntity charge,
                                                             String transactionId,
                                                             ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        return anAuthorisedFailedResponse(charge, transactionId, AuthoriseStatus.CANCELLED, argumentCaptor);
    }

    private GatewayResponse anAuthorisedFailedResponse(ChargeEntity charge,
                                                       String transactionId, AuthoriseStatus authoriseStatus,
                                                       ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(transactionId, authoriseStatus, null, argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        return card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
    }

    private GatewayResponse anAuthorisationErrorResponse(ChargeEntity charge,
                                                         ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(null, AuthoriseStatus.REJECTED, "error-code", argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        return card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
    }


    private HamcrestArgumentMatcher<ChargeEntity> chargeWithStatus(String externalId, ChargeStatus status) {
        return new HamcrestArgumentMatcher<>(new TypeSafeMatcher<ChargeEntity>() {
            @Override
            protected boolean matchesSafely(ChargeEntity chargeEntity) {
                return chargeEntity.getExternalId().equals(externalId) && ChargeStatus.fromString(chargeEntity.getStatus()) == status;
            }

            @Override
            public void describeTo(Description description) {
            }
        });
    }
}
