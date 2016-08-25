package uk.gov.pay.connector.service.sandbox;

import org.junit.Test;
import uk.gov.pay.connector.service.Status;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.*;

public class SandboxStatusMapperTest {

    @Test
    public void shouldGetExpectedStatus_when_AUTHORISED() {

       Status status = SandboxStatusMapper.get().from("AUTHORISED");

        assertThat(status.get(), is(nullValue()));
        assertThat(status.isIgnored(), is(true));
        assertThat(status.isMapped(), is(false));
        assertThat(status.isUnknown(), is(false));
    }

    @Test
    public void shouldGetExpectedStatus_when_CAPTURED() {

       Status status = SandboxStatusMapper.get().from("CAPTURE");

        assertThat(status.get(), is(nullValue()));
        assertThat(status.isIgnored(), is(true));
        assertThat(status.isMapped(), is(false));
        assertThat(status.isUnknown(), is(false));
    }

    @Test
    public void shouldGetExpectedStatus_when_REFUNDED() {

       Status status = SandboxStatusMapper.get().from("REFUNDED");

        assertThat(status.get(), is(nullValue()));
        assertThat(status.isIgnored(), is(true));
        assertThat(status.isMapped(), is(true));
        assertThat(status.isUnknown(), is(false));
    }

    @Test
    public void shouldGetExpectedStatus_when_unknownValue() {

       Status status = SandboxStatusMapper.get().from("whatever");

        assertThat(status.get(), is(nullValue()));
        assertThat(status.isIgnored(), is(true));
        assertThat(status.isMapped(), is(true));
        assertThat(status.isUnknown(), is(false));
    }
}
