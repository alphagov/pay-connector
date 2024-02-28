package uk.gov.pay.connector.charge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseInquiryResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCancelResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.card.service.QueryService;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_CHARGE_MISSING;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.charge.service.AuthorisationErrorGatewayCleanupService.CLEANUP_FAILED;
import static uk.gov.pay.connector.charge.service.AuthorisationErrorGatewayCleanupService.CLEANUP_SUCCESS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;
import static uk.gov.service.payments.commons.model.AuthorisationMode.WEB;

@ExtendWith(MockitoExtension.class)
class AuthorisationErrorGatewayCleanupServiceTest {

    @Mock
    private ChargeDao mockChargeDao;

    @Mock
    private ChargeService mockChargeService;

    @Mock
    private PaymentProviders mockPaymentProviders;

    @Mock
    private PaymentProvider mockWorldpayPaymentProvider;
    @Mock
    private PaymentProvider mockStripePaymentProvider;

    @Mock
    private QueryService mockQueryService;

    @Mock
    private BaseInquiryResponse worldpayQueryResponse;
    @Mock
    private BaseInquiryResponse stripeQueryResponse;

    @Mock
    private WorldpayCancelResponse worldpayCancelResponse;

    @InjectMocks
    private AuthorisationErrorGatewayCleanupService cleanupService;
    private ChargeEntity worldpayCharge;
    private ChargeEntity stripeCharge;

    @BeforeEach
    public void setUp() {
        GatewayAccountEntity worldpayGatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(WORLDPAY.getName())
                .build();
        worldpayCharge = aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGatewayAccountEntity)
                .withPaymentProvider("worldpay")
                .withStatus(ChargeStatus.AUTHORISATION_ERROR)
                .build();

        GatewayAccountEntity stripeGatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(STRIPE.getName())
                .build();
        stripeCharge = aValidChargeEntity()
                .withGatewayAccountEntity(stripeGatewayAccountEntity)
                .withPaymentProvider("stripe")
                .withStatus(ChargeStatus.AUTHORISATION_TIMEOUT)
                .build();
    }

    @Test
    void shouldCleanupChargeThatIsAuthorisedOnTheGateway() throws Exception {
        when(mockChargeDao.findWithPaymentProvidersStatusesAndAuthorisationModesIn(
                eq(List.of(EPDQ.getName(), WORLDPAY.getName(), STRIPE.getName())),
                eq(List.of(AUTHORISATION_ERROR, AUTHORISATION_TIMEOUT, AUTHORISATION_UNEXPECTED_ERROR)),
                eq(List.of(WEB, MOTO_API)),
                any(Integer.class))).thenReturn(List.of(worldpayCharge, stripeCharge));
        when(mockPaymentProviders.byName(WORLDPAY)).thenReturn(mockWorldpayPaymentProvider);
        when(mockPaymentProviders.byName(STRIPE)).thenReturn(mockStripePaymentProvider);
        when(worldpayQueryResponse.getTransactionId()).thenReturn("worldpay-order-code");
        when(stripeQueryResponse.getTransactionId()).thenReturn("stripe-order-code");
        ChargeQueryResponse worldpayChargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, worldpayQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(worldpayCharge))).thenReturn(worldpayChargeQueryResponse);
        ChargeQueryResponse stripeChargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_3DS_REQUIRED, stripeQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(stripeCharge))).thenReturn(stripeChargeQueryResponse);
        when(worldpayCancelResponse.cancelStatus()).thenReturn(BaseCancelResponse.CancelStatus.CANCELLED);

        GatewayResponse worldpayCancelResponse = responseBuilder().withResponse(this.worldpayCancelResponse).build();
        when(mockWorldpayPaymentProvider.cancel(any())).thenReturn(worldpayCancelResponse);
        BaseCancelResponse stripeCancelResponse = buildStripeCancelResponse(stripeCharge.getGatewayTransactionId());
        when(mockStripePaymentProvider.cancel(any())).thenReturn(responseBuilder().withResponse(stripeCancelResponse).build());

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors(10);

        assertThat(result.get(CLEANUP_SUCCESS), is(2));
        assertThat(result.get(CLEANUP_FAILED), is(0));

        verify(mockChargeService).transitionChargeState(eq(worldpayCharge.getExternalId()), eq(AUTHORISATION_ERROR_CANCELLED));
        verify(mockChargeService).transitionChargeState(eq(stripeCharge.getExternalId()), eq(AUTHORISATION_ERROR_CANCELLED));
    }

    @Test
    void shouldSetStripeGatewayTransactionIdOnChargeWhenNull() throws Exception {
        assertThat(stripeCharge.getGatewayTransactionId(), is(nullValue()));

        when(mockChargeDao.findWithPaymentProvidersStatusesAndAuthorisationModesIn(
                eq(List.of(EPDQ.getName(), WORLDPAY.getName(), STRIPE.getName())),
                eq(List.of(AUTHORISATION_ERROR, AUTHORISATION_TIMEOUT, AUTHORISATION_UNEXPECTED_ERROR)),
                eq(List.of(WEB, MOTO_API)),
                any(Integer.class))).thenReturn(List.of(stripeCharge));
        when(mockPaymentProviders.byName(STRIPE)).thenReturn(mockStripePaymentProvider);
        when(stripeQueryResponse.getTransactionId()).thenReturn("stripe-order-code");
        ChargeQueryResponse stripeChargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_3DS_REQUIRED, stripeQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(stripeCharge))).thenReturn(stripeChargeQueryResponse);

        BaseCancelResponse stripeCancelResponse = buildStripeCancelResponse(stripeCharge.getGatewayTransactionId());
        when(mockStripePaymentProvider.cancel(any())).thenReturn(responseBuilder().withResponse(stripeCancelResponse).build());

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors(10);

        assertThat(result.get(CLEANUP_SUCCESS), is(1));
        assertThat(result.get(CLEANUP_FAILED), is(0));

        assertThat(stripeCharge.getGatewayTransactionId(), is("stripe-order-code"));
    }

    @Test
    void shouldTransitionChargeStateToErrorRejectedWhenFailedOnGateway() throws Exception {
        when(mockChargeDao.findWithPaymentProvidersStatusesAndAuthorisationModesIn(
                eq(List.of(EPDQ.getName(), WORLDPAY.getName(), STRIPE.getName())),
                eq(List.of(AUTHORISATION_ERROR, AUTHORISATION_TIMEOUT, AUTHORISATION_UNEXPECTED_ERROR)),
                eq(List.of(WEB, MOTO_API)),
                any(Integer.class))).thenReturn(List.of(worldpayCharge));
        when(worldpayQueryResponse.getTransactionId()).thenReturn("order-code");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_REJECTED, worldpayQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(worldpayCharge))).thenReturn(chargeQueryResponse);

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors(10);

        assertThat(result.get(CLEANUP_SUCCESS), is(1));
        assertThat(result.get(CLEANUP_FAILED), is(0));

        verify(mockChargeService).transitionChargeState(eq(worldpayCharge.getExternalId()), eq(AUTHORISATION_ERROR_REJECTED));
        verify(mockWorldpayPaymentProvider, never()).cancel(any());
    }

    @Test
    void shouldTransitionChargeStateToErrorChargeMissingWhenNotFoundOnGateway() throws Exception {
        when(mockChargeDao.findWithPaymentProvidersStatusesAndAuthorisationModesIn(
                eq(List.of(EPDQ.getName(), WORLDPAY.getName(), STRIPE.getName())),
                eq(List.of(AUTHORISATION_ERROR, AUTHORISATION_TIMEOUT, AUTHORISATION_UNEXPECTED_ERROR)),
                eq(List.of(WEB, MOTO_API)),
                any(Integer.class))).thenReturn(List.of(worldpayCharge));
        when(worldpayQueryResponse.getTransactionId()).thenReturn("");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(null, worldpayQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(worldpayCharge))).thenReturn(chargeQueryResponse);

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors(10);

        assertThat(result.get(CLEANUP_SUCCESS), is(1));
        assertThat(result.get(CLEANUP_FAILED), is(0));

        verify(mockChargeService).transitionChargeState(eq(worldpayCharge.getExternalId()), eq(AUTHORISATION_ERROR_CHARGE_MISSING));
        verify(mockWorldpayPaymentProvider, never()).cancel(any());
    }

    @Test
    void shouldTransitionChargeStateToErrorCancelledWhenAlreadyCancelledOnGateway() throws Exception {
        when(mockChargeDao.findWithPaymentProvidersStatusesAndAuthorisationModesIn(
                eq(List.of(EPDQ.getName(), WORLDPAY.getName(), STRIPE.getName())),
                eq(List.of(AUTHORISATION_ERROR, AUTHORISATION_TIMEOUT, AUTHORISATION_UNEXPECTED_ERROR)),
                eq(List.of(WEB, MOTO_API)),
                any(Integer.class))).thenReturn(List.of(worldpayCharge));
        when(worldpayQueryResponse.getTransactionId()).thenReturn("order-code");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(USER_CANCELLED, worldpayQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(worldpayCharge))).thenReturn(chargeQueryResponse);

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors(10);

        assertThat(result.get(CLEANUP_SUCCESS), is(1));
        assertThat(result.get(CLEANUP_FAILED), is(0));

        verify(mockChargeService).transitionChargeState(eq(worldpayCharge.getExternalId()), eq(AUTHORISATION_ERROR_CANCELLED));
        verify(mockWorldpayPaymentProvider, never()).cancel(any());
    }

    @Test
    void shouldReportFailureWhenGatewayCancelFails() throws Exception {
        when(mockPaymentProviders.byName(WORLDPAY)).thenReturn(mockWorldpayPaymentProvider);
        when(mockChargeDao.findWithPaymentProvidersStatusesAndAuthorisationModesIn(
                eq(List.of(EPDQ.getName(), WORLDPAY.getName(), STRIPE.getName())),
                eq(List.of(AUTHORISATION_ERROR, AUTHORISATION_TIMEOUT, AUTHORISATION_UNEXPECTED_ERROR)),
                eq(List.of(WEB, MOTO_API)),
                any(Integer.class))).thenReturn(List.of(worldpayCharge));
        when(worldpayQueryResponse.getTransactionId()).thenReturn("order-code");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, worldpayQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(worldpayCharge))).thenReturn(chargeQueryResponse);

        when(worldpayCancelResponse.cancelStatus()).thenReturn(BaseCancelResponse.CancelStatus.ERROR);
        GatewayResponse cancelResponse = responseBuilder().withResponse(worldpayCancelResponse).build();
        when(mockWorldpayPaymentProvider.cancel(any())).thenReturn(cancelResponse);

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors(10);

        assertThat(result.get(CLEANUP_SUCCESS), is(0));
        assertThat(result.get(CLEANUP_FAILED), is(1));

        verify(mockChargeService, never()).transitionChargeState(eq(worldpayCharge.getExternalId()), any());
    }

    @Test
    void shouldReportFailureWhenGatewayStatusMapsToUnhandledStatus() throws Exception {
        when(mockChargeDao.findWithPaymentProvidersStatusesAndAuthorisationModesIn(
                eq(List.of(EPDQ.getName(), WORLDPAY.getName(), STRIPE.getName())),
                eq(List.of(AUTHORISATION_ERROR, AUTHORISATION_TIMEOUT, AUTHORISATION_UNEXPECTED_ERROR)),
                eq(List.of(WEB, MOTO_API)),
                any(Integer.class))).thenReturn(List.of(worldpayCharge));
        when(worldpayQueryResponse.getTransactionId()).thenReturn("order-code");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_UNEXPECTED_ERROR, worldpayQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(worldpayCharge))).thenReturn(chargeQueryResponse);

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors(10);

        assertThat(result.get(CLEANUP_SUCCESS), is(0));
        assertThat(result.get(CLEANUP_FAILED), is(1));

        verify(mockWorldpayPaymentProvider, never()).cancel(any());
        verify(mockChargeService, never()).transitionChargeState(eq(worldpayCharge.getExternalId()), any());
    }

    @Test
    void shouldReportFailureWhenGatewayStatusDoesNotMapToInternalStatus() throws Exception {
        when(mockChargeDao.findWithPaymentProvidersStatusesAndAuthorisationModesIn(
                eq(List.of(EPDQ.getName(), WORLDPAY.getName(), STRIPE.getName())),
                eq(List.of(AUTHORISATION_ERROR, AUTHORISATION_TIMEOUT, AUTHORISATION_UNEXPECTED_ERROR)),
                eq(List.of(WEB, MOTO_API)),
                any(Integer.class))).thenReturn(List.of(worldpayCharge));
        when(worldpayQueryResponse.getTransactionId()).thenReturn("order-code");
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(null, worldpayQueryResponse);
        when(mockQueryService.getChargeGatewayStatus(eq(worldpayCharge))).thenReturn(chargeQueryResponse);

        Map<String, Integer> result = cleanupService.sweepAndCleanupAuthorisationErrors(10);

        assertThat(result.get(CLEANUP_SUCCESS), is(0));
        assertThat(result.get(CLEANUP_FAILED), is(1));

        verify(mockWorldpayPaymentProvider, never()).cancel(any());
        verify(mockChargeService, never()).transitionChargeState(eq(worldpayCharge.getExternalId()), any());
    }

    private BaseCancelResponse buildStripeCancelResponse(String transactionId) {
        return new BaseCancelResponse() {
            @Override
            public String getErrorCode() {
                return null;
            }

            @Override
            public String getErrorMessage() {
                return null;
            }

            @Override
            public String getTransactionId() {
                return transactionId;
            }

            @Override
            public CancelStatus cancelStatus() {
                return CancelStatus.CANCELLED;
            }
        };
    }
}
