package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.service.StatusFlow.EXPIRE_FLOW;
import static uk.gov.pay.connector.charge.service.StatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.charge.service.StatusFlow.USER_CANCELLATION_FLOW;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_AUTHORISATION_REFUSED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_AUTHORISED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_AUTHORISED_CANCELLED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_DELETION_REFUSED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_PAYMENT_DELETED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_PAYMENT_REQUESTED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_REFUND;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_REFUND_DECLINED_BY_ACQUIRER;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_REFUND_REFUSED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.UNKNOWN;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;

public class EpdqNotificationServiceStatusMapperTest extends EpdqNotificationServiceTest {

    @Before
    public void setup() {
        super.setup();
    }

    @Test
    public void shouldUpdateChargeToAuthorisationRejected_IfEpdqStatusIs2() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISATION_REFUSED);
        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor).invoke(payId, mockCharge, AUTHORISATION_REJECTED, null);
    }

    @Test
    public void shouldUpdateChargeToAuthorisationSuccess_IfEpdqStatusIs5() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED);
        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor).invoke(payId, mockCharge, AUTHORISATION_SUCCESS, null);
    }

    @Test
    public void shouldUpdateChargeToCaptured_IfEpdqStatusIs9() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_PAYMENT_REQUESTED);
        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor).invoke(payId, mockCharge, CAPTURED, null);
    }

    @Test
    public void shouldUpdateChargeToSystemCancel_IfEpdqStatusIs6WithRelevantChargeStatus() {

        List<ChargeStatus> ChargeStatuses = ImmutableList.of(SYSTEM_CANCEL_SUBMITTED, CREATED, ENTERING_CARD_DETAILS);

        for (ChargeStatus chargeStatus : ChargeStatuses) {
            when(mockCharge.getStatus()).thenReturn(chargeStatus.getValue());
            final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);
            notificationService.handleNotificationFor(payload);
        }
        verify(mockChargeNotificationProcessor, times(3)).invoke(payId, mockCharge, SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState(), null);
    }

    @Test
    public void shouldUpdateChargeToUserCancel_IfEpdqStatusIs6WithRelevantChargeStatus() {
        when(mockCharge.getStatus()).thenReturn(USER_CANCEL_SUBMITTED.getValue());
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);

        notificationService.handleNotificationFor(payload);
        verify(mockChargeNotificationProcessor).invoke(payId, mockCharge, USER_CANCELLATION_FLOW.getSuccessTerminalState(), null);
    }

    @Test
    public void shouldUpdateChargeToExpire_IfEpdqStatusIs6WithRelevantChargeStatus() {
        when(mockCharge.getStatus()).thenReturn(EXPIRE_CANCEL_SUBMITTED.getValue());
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);

        notificationService.handleNotificationFor(payload);
        verify(mockChargeNotificationProcessor).invoke(payId, mockCharge, EXPIRE_FLOW.getSuccessTerminalState(), null);
    }

    @Test
    public void shouldUpdateChargeToSystemCancel_IfEpdqStatusIs6AndChargeStatusIsNotRelevant() {
        when(mockCharge.getStatus()).thenReturn(CAPTURED.getValue());
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);

        notificationService.handleNotificationFor(payload);
        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldNotUpdateCharge_IfEpdqStatusIsUnknown() {
        final String payload = notificationPayloadForTransaction(payId, UNKNOWN);
        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldRefund_IfEpdqStatusIs7() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_PAYMENT_DELETED);

        notificationService.handleNotificationFor(payload);
        verify(mockRefundNotificationProcessor).invoke(PaymentGatewayName.EPDQ, REFUNDED, payId + "/" + payIdSub, payId);
    }

    @Test
    public void shouldRefund_IfEpdqStatusIs8() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_REFUND);

        notificationService.handleNotificationFor(payload);
        verify(mockRefundNotificationProcessor).invoke(PaymentGatewayName.EPDQ, REFUNDED, payId + "/" + payIdSub, payId);
    }

    @Test
    public void shouldBeARefundError_IfEpdqStatusIs83() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_REFUND_REFUSED);

        notificationService.handleNotificationFor(payload);
        verify(mockRefundNotificationProcessor).invoke(PaymentGatewayName.EPDQ, REFUND_ERROR, payId + "/" + payIdSub, payId);
    }

    @Test
    public void shouldBeARefundError_IfEpdqStatusIs73() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_DELETION_REFUSED);

        notificationService.handleNotificationFor(payload);
        verify(mockRefundNotificationProcessor).invoke(PaymentGatewayName.EPDQ, REFUND_ERROR, payId + "/" + payIdSub, payId);
    }

    @Test
    public void shouldBeARefundError_IfEpdqStatusIs94() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_REFUND_DECLINED_BY_ACQUIRER);

        notificationService.handleNotificationFor(payload);
        verify(mockRefundNotificationProcessor).invoke(PaymentGatewayName.EPDQ, REFUND_ERROR, payId + "/" + payIdSub, payId);
    }

    @Test
    public void shouldNotProcessRefund_IfEpdqStatusIsUnknown() {
        final String payload = notificationPayloadForTransaction(payId, UNKNOWN);

        notificationService.handleNotificationFor(payload);
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }
}
