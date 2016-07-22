package uk.gov.pay.connector.model.api;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ExternalRefundStatusTest {

    @Test
    public void shouldHaveCorrectNumberOfStatuses() {
        assertThat(ExternalRefundStatus.values().length, is(3));
    }

    @Test
    public void shouldGetStatusValue() {
        assertThat(ExternalRefundStatus.EXTERNAL_SUBMITTED.getStatus(), is("submitted"));
        assertThat(ExternalRefundStatus.EXTERNAL_SUCCESS.getStatus(), is("success"));
        assertThat(ExternalRefundStatus.EXTERNAL_ERROR.getStatus(), is("error"));
    }
}
