package uk.gov.pay.connector.gateway.smartpay;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;

public class SmartpayStatusMapperTest {

    @Test
    public void shouldReturnAStatusForCaptureTrue() {
        Pair<String, Boolean> value = Pair.of("CAPTURE", true);
        InterpretedStatus status = SmartpayStatusMapper.from(value, CAPTURE_SUBMITTED);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(status.getChargeStatus(), is(CAPTURED));
    }

    @Test
    public void shouldReturnAStatusForCaptureFalse() {
        Pair<String, Boolean> value = Pair.of("CAPTURE", false);
        InterpretedStatus status = SmartpayStatusMapper.from(value, CAPTURE_SUBMITTED);

        assertThat(status.getType(), is(InterpretedStatus.Type.CHARGE_STATUS));
        assertThat(status.getChargeStatus(), is(CAPTURE_ERROR));
    }

    @Test
    public void shouldReturnAStatusForRefundedTrue() {
        Pair<String, Boolean> value = Pair.of("REFUND", true);
        InterpretedStatus status = SmartpayStatusMapper.from(value, CAPTURED);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUNDED));
    }

    @Test
    public void shouldReturnAStatusForRefundedFalse() {
        Pair<String, Boolean> value = Pair.of("REFUND", false);
        InterpretedStatus status = SmartpayStatusMapper.from(value, CAPTURED);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnAStatusForRefundFailedTrue() {
        Pair<String, Boolean> value = Pair.of("REFUND_FAILED", true);
        InterpretedStatus status = SmartpayStatusMapper.from(value, CAPTURED);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnAStatusForRefundFailedFalse() {
        Pair<String, Boolean> value = Pair.of("REFUND_FAILED", false);
        InterpretedStatus status = SmartpayStatusMapper.from(value, CAPTURED);

        assertThat(status.getType(), is(InterpretedStatus.Type.REFUND_STATUS));
        assertThat(status.getRefundStatus(), is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsUnknown() {
        InterpretedStatus status = SmartpayStatusMapper.from(Pair.of("UNKNOWN", true), AUTHORISATION_SUCCESS);

        assertThat(status.getType(), is(InterpretedStatus.Type.UNKNOWN));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsIgnored() {
        InterpretedStatus status = SmartpayStatusMapper.from(Pair.of("AUTHORISATION", true), AUTHORISATION_SUCCESS);

        assertThat(status.getType(), is(InterpretedStatus.Type.IGNORED));
    }

}
