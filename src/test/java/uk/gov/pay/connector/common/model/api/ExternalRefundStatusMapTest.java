package uk.gov.pay.connector.common.model.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

class ExternalRefundStatusMapTest {

    @Test
    void shouldHaveCorrectNumberOfStatuses() {
        assertThat(ExternalRefundStatus.values().length, is(3));
    }

    @Test
    void shouldGetStatusValue() {
        assertThat(ExternalRefundStatus.EXTERNAL_SUBMITTED.getStatus(), is("submitted"));
        assertThat(ExternalRefundStatus.EXTERNAL_SUCCESS.getStatus(), is("success"));
        assertThat(ExternalRefundStatus.EXTERNAL_ERROR.getStatus(), is("error"));
    }

    @Test()
    void shouldThrowExceptionForUnrecognisedStatusLabelValue() {

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            ExternalRefundStatus.fromPublicStatusLabel("whatever");
        });
    }

    @Test()
    void shouldThrowExceptionForEmptyStatusLabelValue() {

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            ExternalRefundStatus.fromPublicStatusLabel("");
        });
    }

}
