package uk.gov.pay.connector.service.sandbox;

import org.junit.Test;
import uk.gov.pay.connector.service.Status;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;

public class SandboxStatusMapperTest {

    @Test
    public void shouldGetExpectedStatus_when_AUTHORISED() {

        Status status = SandboxStatusMapper.get().from("AUTHORISED");

        assertThat(status.isIgnored(), is(true));
        assertThat(status.isMapped(), is(false));
        assertThat(status.isUnknown(), is(false));
    }

    @Test
    public void shouldGetExpectedStatus_when_CAPTURED() {

        Status status = SandboxStatusMapper.get().from("CAPTURED");

        assertThat(status.get(), is(CAPTURED));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isMapped(), is(true));
        assertThat(status.isUnknown(), is(false));
    }

    @Test
    public void shouldGetExpectedStatus_when_REFUNDED() {

        Status status = SandboxStatusMapper.get().from("REFUNDED");

        assertThat(status.get(), is(REFUNDED));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isMapped(), is(true));
        assertThat(status.isUnknown(), is(false));
    }

    @Test
    public void shouldGetExpectedStatus_when_unknownValue() {

        Status status = SandboxStatusMapper.get().from("whatever");

        assertThat(status.isIgnored(), is(false));
        assertThat(status.isMapped(), is(false));
        assertThat(status.isUnknown(), is(true));
    }
}
