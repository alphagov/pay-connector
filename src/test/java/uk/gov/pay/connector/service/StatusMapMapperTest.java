package uk.gov.pay.connector.service;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class StatusMapMapperTest {

    ChargeStatus chargeStatus = ChargeStatus.CAPTURE_APPROVED;

    BaseStatusMapper<String> baseStatusMapper = BaseStatusMapper.<String>builder()
            .map("status_1", chargeStatus)
            .ignore("status_2")
            .build();

    @Test
    public void shouldReturnAStatus() {
        InterpretedStatus status = baseStatusMapper.from("status_1");

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.get().get(), is(chargeStatus));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsUnknown() {
        InterpretedStatus status = baseStatusMapper.from("status_3");

        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(true));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsIgnored() {
        InterpretedStatus status = baseStatusMapper.from("status_2");

        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(true));
        assertThat(status.isUnknown(), is(false));
    }

}
