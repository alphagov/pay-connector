package uk.gov.pay.connector.service;

import org.junit.Test;
import uk.gov.pay.connector.service.StatusMapper.Status;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class StatusMapMapperTest {

    public enum TestStatus {
        STATUS_1
    }

    StatusMapper<String, TestStatus> statusMapper = StatusMapper.<String, TestStatus>builder()
            .map("status_1", TestStatus.STATUS_1)
            .ignore("status_2")
            .build();

    @Test
    public void shouldReturnAStatus() throws Exception {
        Status<TestStatus> status = statusMapper.from("status_1");

        assertThat(status.isMapped(), is(true));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(false));
        assertThat(status.get(), is(TestStatus.STATUS_1));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsUnknown() throws Exception {
        Status<TestStatus> status = statusMapper.from("status_3");

        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(false));
        assertThat(status.isUnknown(), is(true));
    }

    @Test
    public void shouldReturnEmptyWhenStatusIsIgnored() throws Exception {
        Status<TestStatus> status = statusMapper.from("status_2");

        assertThat(status.isMapped(), is(false));
        assertThat(status.isIgnored(), is(true));
        assertThat(status.isUnknown(), is(false));
    }

}