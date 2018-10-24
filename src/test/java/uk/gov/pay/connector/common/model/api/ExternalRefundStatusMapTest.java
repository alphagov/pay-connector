package uk.gov.pay.connector.common.model.api;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ExternalRefundStatusMapTest {

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

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionForUnrecognisedStatusLabelValue() {

        ExternalRefundStatus.fromPublicStatusLabel("whatever");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionForEmptyStatusLabelValue() {

        ExternalRefundStatus.fromPublicStatusLabel("");
    }
}
