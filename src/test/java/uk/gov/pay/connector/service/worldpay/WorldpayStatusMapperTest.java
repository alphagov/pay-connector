package uk.gov.pay.connector.service.worldpay;

import org.junit.Test;
import uk.gov.pay.connector.service.InterpretedStatus;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;

public class WorldpayStatusMapperTest {

    @Test
    public void shouldReturnAChargeStatus() throws Exception {
        InterpretedStatus status = WorldpayStatusMapper.get().from("CAPTURED");

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.isDeferred(), is(false));
        assertThat(status.get().get(), is(CAPTURED));
    }

    @Test
    public void shouldReturnARefundStatusFromRefunded() throws Exception {
        InterpretedStatus status = WorldpayStatusMapper.get().from("REFUNDED");

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.isDeferred(), is(false));
        assertThat(status.get().get(), is(REFUNDED));
    }

    @Test
    public void shouldReturnARefundStatusFromRefundedByMerchant() throws Exception {
        InterpretedStatus status = WorldpayStatusMapper.get().from("REFUNDED_BY_MERCHANT");

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.isDeferred(), is(false));
        assertThat(status.get().get(), is(REFUNDED));
    }


    @Test
    public void shouldReturnEmptyWhenStatusIsUnknown() throws Exception {
        InterpretedStatus status = WorldpayStatusMapper.get().from("unknown");

        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(true));
        assertThat(status.isDeferred(), is(false));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsUnhandled() throws Exception {
        InterpretedStatus status = WorldpayStatusMapper.get().from("AUTHORISED");

        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(true));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.isDeferred(), is(false));
    }

}
