package uk.gov.pay.connector.charge.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseInquiryResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentprocessor.service.QueryService;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_CHARGE_MISSING;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.service.EpdqAuthorisationErrorGatewayCleanupService.CLEANUP_FAILED;
import static uk.gov.pay.connector.charge.service.EpdqAuthorisationErrorGatewayCleanupService.CLEANUP_SUCCESS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;

@RunWith(MockitoJUnitRunner.class)
public class EpdqAuthorisationErrorGatewayCleanupServiceTest {
    
    @Mock
    private ChargeDao mockChargeDao;

    @Mock
    private ChargeService mockChargeService;

    @Mock
    private PaymentProviders mockPaymentProviders;

    @Mock
    private PaymentProvider mockPaymentProvider;

    @Mock
    private QueryService mockQueryService;
    
    @Mock
    private BaseInquiryResponse mockQueryResponse;
    
    @Mock
    private EpdqCancelResponse epdqCancelResponse;
    
    @InjectMocks
    private EpdqAuthorisationErrorGatewayCleanupService cleanupService;
    private ChargeEntity charge;

    @Before
    public void setUp() {
        when(mockPaymentProviders.byName(EPDQ)).thenReturn(mockPaymentProvider);

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(EPDQ.getName())
                .build();
        charge = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withStatus(ChargeStatus.AUTHORISATION_ERROR)
                .build();

        when(mockChargeDao.findWithPaymentProviderAndStatusIn(EPDQ.getName(), List.of(
                AUTHORISATION_ERROR,
                AUTHORISATION_TIMEOUT,
                AUTHORISATION_UNEXPECTED_ERROR
        ))).thenReturn(List.of(charge));
    }

    @Test
    public void shouldCleanupChargeThatIsAuthorisedOnTheGateway() throws Exception {
        when(mockQueryResponse.getTransactionId()).thenReturn("order-code");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, mockQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(charge))).thenReturn(chargeQueryResponse);
        
        when(epdqCancelResponse.cancelStatus()).thenReturn(BaseCancelResponse.CancelStatus.CANCELLED);
        GatewayResponse cancelResponse = responseBuilder().withResponse(epdqCancelResponse).build();
        when(mockPaymentProvider.cancel(any())).thenReturn(cancelResponse);

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors();
        
        assertThat(result.get(CLEANUP_SUCCESS), is(1));
        assertThat(result.get(CLEANUP_FAILED), is(0));
        
        verify(mockChargeService).transitionChargeState(eq(charge), eq(AUTHORISATION_ERROR_CANCELLED));
    }

    @Test
    public void shouldTransitionChargeStateToErrorRejectedWhenFailedOnGateway() throws Exception {
        when(mockQueryResponse.getTransactionId()).thenReturn("order-code");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_REJECTED, mockQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(charge))).thenReturn(chargeQueryResponse);

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors();

        assertThat(result.get(CLEANUP_SUCCESS), is(1));
        assertThat(result.get(CLEANUP_FAILED), is(0));

        verify(mockChargeService).transitionChargeState(eq(charge), eq(AUTHORISATION_ERROR_REJECTED));
    }

    @Test
    public void shouldTransitionChargeStateToErrorChargeMissingWhenNotFoundOnGateway() throws Exception {
        when(mockQueryResponse.getTransactionId()).thenReturn("");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(null, mockQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(charge))).thenReturn(chargeQueryResponse);

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors();

        assertThat(result.get(CLEANUP_SUCCESS), is(1));
        assertThat(result.get(CLEANUP_FAILED), is(0));

        verify(mockChargeService).transitionChargeState(eq(charge), eq(AUTHORISATION_ERROR_CHARGE_MISSING));
    }

    @Test
    public void shouldReportFailureWhenGatewayCancelFails() throws Exception {
        when(mockQueryResponse.getTransactionId()).thenReturn("order-code");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, mockQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(charge))).thenReturn(chargeQueryResponse);

        when(epdqCancelResponse.cancelStatus()).thenReturn(BaseCancelResponse.CancelStatus.ERROR);
        GatewayResponse cancelResponse = responseBuilder().withResponse(epdqCancelResponse).build();
        when(mockPaymentProvider.cancel(any())).thenReturn(cancelResponse);

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors();

        assertThat(result.get(CLEANUP_SUCCESS), is(0));
        assertThat(result.get(CLEANUP_FAILED), is(1));

        verify(mockChargeService, never()).transitionChargeState(eq(charge), any());
    }

    @Test
    public void shouldReportFailureWhenGatewayStatusMapsToUnhandledStatus() throws Exception {
        when(mockQueryResponse.getTransactionId()).thenReturn("order-code");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_UNEXPECTED_ERROR, mockQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(charge))).thenReturn(chargeQueryResponse);

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors();

        assertThat(result.get(CLEANUP_SUCCESS), is(0));
        assertThat(result.get(CLEANUP_FAILED), is(1));

        verify(mockPaymentProvider, never()).cancel(any());
        verify(mockChargeService, never()).transitionChargeState(eq(charge), any());
    }
    
    @Test
    public void shouldReportFailureWhenGatewayStatusDoesNotMapToInternalStatus() throws Exception {
        when(mockQueryResponse.getTransactionId()).thenReturn("order-code");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(null, mockQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(charge))).thenReturn(chargeQueryResponse);

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors();

        assertThat(result.get(CLEANUP_SUCCESS), is(0));
        assertThat(result.get(CLEANUP_FAILED), is(1));

        verify(mockPaymentProvider, never()).cancel(any());
        verify(mockChargeService, never()).transitionChargeState(eq(charge), any());
    }
}
