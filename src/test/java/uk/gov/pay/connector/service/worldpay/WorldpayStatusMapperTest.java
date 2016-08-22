package uk.gov.pay.connector.service.worldpay;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.StatusMapper.Status;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;

public class WorldpayStatusMapperTest {

    @Test
    public void shouldReturnAStatus() throws Exception {
        Status<ChargeStatus> status = WorldpayStatusMapper.from("CAPTURED");

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.get(), is(CAPTURED));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsUnknown() throws Exception {
        Status<ChargeStatus> status = WorldpayStatusMapper.from("unknown");

        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(true));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsUnhandled() throws Exception {
        Status<ChargeStatus> status = WorldpayStatusMapper.from("AUTHORISED");

        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(true));
        assertThat(status.isUnknown(), is(false));
    }

}