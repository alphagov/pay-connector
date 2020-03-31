package uk.gov.pay.connector.gateway.smartpay;

import org.junit.Test;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;

public class SmartpayStatusMapperTest {

    @Test
    public void shouldReturnAStatusForCaptureTrue() {
        SmartpayStatus value = new SmartpayStatus("CAPTURE", true);
        InterpretedStatus status = SmartpayStatusMapper.from(value);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(status.getChargeStatus(), is(CAPTURED));
    }

    @Test
    public void shouldReturnAStatusForCaptureFalse() {
        SmartpayStatus value = new SmartpayStatus("CAPTURE", false);
        InterpretedStatus status = SmartpayStatusMapper.from(value);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(status.getChargeStatus(), is(CAPTURE_ERROR));
    }

    @Test
    public void shouldReturnAStatusForRefundedTrue() {
        SmartpayStatus value = new SmartpayStatus("REFUND", true);
        InterpretedStatus status = SmartpayStatusMapper.from(value);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUNDED));
    }

    @Test
    public void shouldReturnAStatusForRefundedFalse() {
        SmartpayStatus value = new SmartpayStatus("REFUND", false);
        InterpretedStatus status = SmartpayStatusMapper.from(value);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnAStatusForRefundFailedTrue() {
        SmartpayStatus value = new SmartpayStatus("REFUND_FAILED", true);
        InterpretedStatus status = SmartpayStatusMapper.from(value);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnAStatusForRefundFailedFalse() {
        SmartpayStatus value = new SmartpayStatus("REFUND_FAILED", false);
        InterpretedStatus status = SmartpayStatusMapper.from(value);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsUnknown() {
        InterpretedStatus status = SmartpayStatusMapper.from(new SmartpayStatus("UNKNOWN", true));

        assertThat(status.getType(), is(InterpretedStatus.Type.UNKNOWN));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsIgnored() {
        InterpretedStatus status = SmartpayStatusMapper.from(new SmartpayStatus("AUTHORISATION", true));

        assertThat(status.getType(), is(InterpretedStatus.Type.IGNORED));
    }

}
