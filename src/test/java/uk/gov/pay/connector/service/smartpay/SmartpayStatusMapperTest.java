package uk.gov.pay.connector.service.smartpay;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import uk.gov.pay.connector.service.Status;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_ERROR;

public class SmartpayStatusMapperTest {

    @Test
    public void shouldReturnAStatusForCaptureTrue() throws Exception {
        Pair<String, Boolean> value = Pair.of("CAPTURE", true);
        Status status = SmartpayStatusMapper.get().from(value);

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.get(), is(CAPTURED));
    }

    @Test
    public void shouldReturnAStatusForCaptureFalse() throws Exception {
        Pair<String, Boolean> value = Pair.of("CAPTURE", false);
        Status status = SmartpayStatusMapper.get().from(value);

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.get(), is(CAPTURE_ERROR));
    }

    @Test
    public void shouldReturnAStatusForRefundedTrue() throws Exception {
        Pair<String, Boolean> value = Pair.of("REFUND", true);
        Status status = SmartpayStatusMapper.get().from(value);

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.get(), is(REFUNDED));
    }

    @Test
    public void shouldReturnAStatusForRefundedFalse() throws Exception {
        Pair<String, Boolean> value = Pair.of("REFUND", false);
        Status status = SmartpayStatusMapper.get().from(value);

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.get(), is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnAStatusForRefundFailedTrue() throws Exception {
        Pair<String, Boolean> value = Pair.of("REFUND_FAILED", true);
        Status status = SmartpayStatusMapper.get().from(value);

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.get(), is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnAStatusForRefundFailedFalse() throws Exception {
        Pair<String, Boolean> value = Pair.of("REFUND_FAILED", false);
        Status status = SmartpayStatusMapper.get().from(value);

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.get(), is(REFUND_ERROR));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsUnknown() throws Exception {
        Status status = SmartpayStatusMapper.get().from(Pair.of("UNKNOWN", true));

        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(true));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsIgnored() throws Exception {
        Status status = SmartpayStatusMapper.get().from(Pair.of("AUTHORISATION", true));

        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(true));
        assertThat(status.isUnknown(), is(false));
    }

}
