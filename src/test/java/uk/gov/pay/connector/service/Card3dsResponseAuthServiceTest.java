package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.exception.*;
import uk.gov.pay.connector.model.domain.Auth3dsDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.service.worldpay.WorldpayOrderStatusResponse;
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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus.IN_PROGRESS;

@RunWith(MockitoJUnitRunner.class)
public class Card3dsResponseAuthServiceTest extends CardServiceTest {
    private static final String GENERATED_TRANSACTION_ID = "generated-transaction-id";

    private ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_3DS_REQUIRED, GENERATED_TRANSACTION_ID);
    private ChargeEntity reloadedCharge = spy(charge);

    private Card3dsResponseAuthService card3dsResponseAuthService;
    private CardExecutorService mockExecutorService = mock(CardExecutorService.class);

    @Before
    public void setUpCardAuthorisationService() {
        mockMetricRegistry = mock(MetricRegistry.class);
        Counter mockCounter = mock(Counter.class);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        card3dsResponseAuthService = new Card3dsResponseAuthService(mockedChargeDao, mockedProviders, mockExecutorService, mockMetricRegistry);
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
        GatewayResponse authorisationResponse = GatewayResponse.with(worldpayResponse);
        when(mockedPaymentProvider.authorise3dsResponse(argumentCaptor.capture())).thenReturn(authorisationResponse);
    }

    @Test
    public void shouldRespondAuthorisationSuccess() throws Exception {
        Auth3dsDetails auth3dsDetails = AuthUtils.buildAuth3dsDetails();
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        GatewayResponse response = anAuthorisationSuccessResponse(charge, reloadedCharge, charge.getGatewayTransactionId(), auth3dsDetails, argumentCaptor);

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_SUCCESS.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        assertTrue(argumentCaptor.getValue().getTransactionId().isPresent());
        assertThat(argumentCaptor.getValue().getTransactionId().get(), is(GENERATED_TRANSACTION_ID));

    }


    @Test
    public void shouldRetainGeneratedTransactionIdIfAuthorisationAborted() throws Exception {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        setupMockExecutorServiceMock();

        when(mockedPaymentProvider.authorise3dsResponse(any())).thenThrow(RuntimeException.class);

        try {
            card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
            fail("Won’t get this far");
        } catch (RuntimeException e) {
            assertThat(reloadedCharge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        }
    }

    @Test
    public void shouldRespondAuthorisationRejected() throws Exception {
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_3DS_REQUIRED, GENERATED_TRANSACTION_ID);
        ChargeEntity reloadedCharge = spy(charge);
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);

        GatewayResponse response = anAuthorisationRejectedResponse(charge, reloadedCharge, charge.getGatewayTransactionId(), argumentCaptor);

        assertThat(response.isSuccessful(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_REJECTED.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
        assertTrue(argumentCaptor.getValue().getTransactionId().isPresent());
        assertThat(argumentCaptor.getValue().getTransactionId().get(), is(GENERATED_TRANSACTION_ID));
    }

    @Test
    public void shouldRespondAuthorisationError() throws Exception {
        ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor = ArgumentCaptor.forClass(Auth3dsResponseGatewayRequest.class);
        GatewayResponse response = anAuthorisationErrorResponse(charge, reloadedCharge, argumentCaptor);

        assertThat(response.isFailed(), is(true));
        assertThat(reloadedCharge.getStatus(), is(AUTHORISATION_ERROR.toString()));
        assertThat(reloadedCharge.getGatewayTransactionId(), is(GENERATED_TRANSACTION_ID));
    }

    @Test
    public void authoriseShouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenTimeout() throws Exception {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);
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

        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());

        card3dsResponseAuthService.doAuthorise(chargeId, AuthUtils.buildAuth3dsDetails());
    }

    @Test(expected = OperationAlreadyInProgressRuntimeException.class)
    public void shouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenStatusIsAuthorisation3dsReady() {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_3DS_READY);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);

        setupMockExecutorServiceMock();

        card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void shouldThrowAnIllegalStateRuntimeExceptionWhenInvalidStatus() throws Exception {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_READY);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);

        setupMockExecutorServiceMock();

        card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = ConflictRuntimeException.class)
    public void shouldThrowAConflictRuntimeExceptionWhenOptimisticExceptionThrownAtChargeReload() throws Exception {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenThrow(new OptimisticLockException());

        setupMockExecutorServiceMock();

        card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = ChargeExpiredRuntimeException.class)
    public void shouldThrowChargeExpiredRuntimeExceptionWhenChargeExpired() {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.EXPIRED);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);

        setupMockExecutorServiceMock();

        card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    private GatewayResponse anAuthorisationRejectedResponse(ChargeEntity charge, ChargeEntity reloadedCharge,
                                                            String transactionId,
                                                            ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(transactionId, AuthoriseStatus.REJECTED, null, argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());

        return card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
    }

    private GatewayResponse anAuthorisationSuccessResponse(ChargeEntity charge, ChargeEntity reloadedCharge,
                                                           String transactionId, Auth3dsDetails auth3dsDetails,
                                                           ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(transactionId, AuthoriseStatus.AUTHORISED, null, argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        return card3dsResponseAuthService.doAuthorise(charge.getExternalId(), auth3dsDetails);
    }

    private GatewayResponse anAuthorisationErrorResponse(ChargeEntity charge, ChargeEntity reloadedCharge,
                                                         ArgumentCaptor<Auth3dsResponseGatewayRequest> argumentCaptor) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge)).thenReturn(reloadedCharge);
        when(mockedChargeDao.merge(reloadedCharge)).thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(null, AuthoriseStatus.REJECTED, "error-code", argumentCaptor);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        return card3dsResponseAuthService.doAuthorise(charge.getExternalId(), AuthUtils.buildAuth3dsDetails());
    }
}
