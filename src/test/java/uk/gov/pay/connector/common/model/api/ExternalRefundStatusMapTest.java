package uk.gov.pay.connector.common.model.api;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void shouldThrowExceptionForUnrecognisedStatusLabelValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            ExternalRefundStatus.fromPublicStatusLabel("whatever");
        });
    }

    @Test
    void shouldThrowExceptionForEmptyStatusLabelValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            ExternalRefundStatus.fromPublicStatusLabel("");
        });
    }

}
