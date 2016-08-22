package uk.gov.pay.connector.service.smartpay;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.StatusMapper.Status;
import uk.gov.pay.connector.service.worldpay.WorldpayStatusMapper;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;

public class SmartpayStatusMapperTest {

    @Test
    public void shouldReturnAStatus() throws Exception {
        Pair<String, Boolean> value = Pair.of("CAPTURE", true);
        Status<ChargeStatus> status = SmartpayStatusMapper.from(value);

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
    public void shouldReturnEmptyWhenStatusIsIgnored() throws Exception {
        Status<ChargeStatus> status = WorldpayStatusMapper.from("AUTHORISED");

        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(true));
        assertThat(status.isUnknown(), is(false));
    }

}