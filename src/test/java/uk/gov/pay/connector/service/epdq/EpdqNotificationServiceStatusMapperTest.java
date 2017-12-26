package uk.gov.pay.connector.service.epdq;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.EpdqNotificationService;
import uk.gov.pay.connector.service.InterpretedStatus;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRE_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.service.StatusFlow.EXPIRE_FLOW;
import static uk.gov.pay.connector.service.StatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.service.StatusFlow.USER_CANCELLATION_FLOW;

public class EpdqNotificationServiceStatusMapperTest {

    @Test
    public void shouldReturnAuthorisationRejectedStatusFromValue2() throws Exception {
        ChargeStatus status = EpdqNotificationService.newChargeStateForChargeNotification("2", AUTHORISATION_SUBMITTED).get();

        assertThat(status, is(AUTHORISATION_REJECTED));
    }

    @Test
    public void shouldReturnAuthorisationSuccessStatusFromValue5() throws Exception {
        ChargeStatus status = EpdqNotificationService.newChargeStateForChargeNotification("5", AUTHORISATION_SUBMITTED).get();

        assertThat(status, is(AUTHORISATION_SUCCESS));
    }

    @Test
    public void shouldReturnUserCancelledFromValue6WhenCurrentStatusUserCancelSubmitted() throws Exception {
        ChargeStatus status = EpdqNotificationService.newChargeStateForChargeNotification("6", USER_CANCEL_SUBMITTED).get();

        assertThat(status, is(USER_CANCELLATION_FLOW.getSuccessTerminalState()));
    }

    @Test
    public void shouldReturnUserCancelledFromValue6WhenCurrentStatusSystemCancelSubmitted() throws Exception {
        ChargeStatus status = EpdqNotificationService.newChargeStateForChargeNotification("6", SYSTEM_CANCEL_SUBMITTED).get();

        assertThat(status, is(SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState()));
    }

    @Test
    public void shouldReturnUserCancelledFromValue6WhenCurrentStatusExpireCancelSubmitted() throws Exception {
        ChargeStatus status = EpdqNotificationService.newChargeStateForChargeNotification("6", EXPIRE_CANCEL_SUBMITTED).get();

        assertThat(status, is(EXPIRE_FLOW.getSuccessTerminalState()));
    }

    @Test
    public void shouldReturnUserCancelledFromValue6WhenCurrentStatusCreated() throws Exception {
        ChargeStatus status = EpdqNotificationService.newChargeStateForChargeNotification("6", CREATED).get();

        assertThat(status, is(SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState()));
    }

    @Test
    public void shouldReturnUserCancelledFromValue6WhenCurrentStatusEnteringCardDetails() throws Exception {
        ChargeStatus status = EpdqNotificationService.newChargeStateForChargeNotification("6", ENTERING_CARD_DETAILS).get();

        assertThat(status, is(SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState()));
    }

    @Test
    public void shouldReturnCapturedStatusFromValue9() throws Exception {
        ChargeStatus status = EpdqNotificationService.newChargeStateForChargeNotification("9", CAPTURE_SUBMITTED).get();

        assertThat(status, is(CAPTURED));
    }

    @Test
    public void shouldReturnRefundedStatusFromValue8() {
        RefundStatus status = EpdqNotificationService.newRefundStateForRefundNotification("8").get();

        assertThat(status, is(REFUNDED));
    }

    @Test
    public void shouldReturnRefundErrorStatusFromValue83() {

        RefundStatus status = EpdqNotificationService.newRefundStateForRefundNotification("83").get();

        assertThat(status, is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnRefundedStatusFromValue7() {
        RefundStatus status = EpdqNotificationService.newRefundStateForRefundNotification("7").get();

        assertThat(status, is(REFUNDED));
    }

    @Test
    public void shouldReturnRefundErrorStatusFromValue73() {
        RefundStatus status = EpdqNotificationService.newRefundStateForRefundNotification("73").get();

        assertThat(status, is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnUnknownStatusFromUnknownValue() throws Exception {
        Optional<ChargeStatus> chargeStatus = EpdqNotificationService.newChargeStateForChargeNotification("unknown", AUTHORISATION_SUCCESS);
        Optional<RefundStatus> refundStatus = EpdqNotificationService.newRefundStateForRefundNotification("unknown");

        assertThat(chargeStatus.isPresent(), is(false));
        assertThat(refundStatus.isPresent(), is(false));
    }

    @Test
    public void shouldReturnRefundErrorStatusFromValue94() {
        RefundStatus status = EpdqNotificationService.newRefundStateForRefundNotification("94").get();

        assertThat(status, is(REFUND_ERROR));
    }
}
