package uk.gov.pay.connector.gateway.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class StripeAuthorisationRejectedCodeMapperTest {

    @Test
    void shouldBeUncategorisedIfStripeDeclineCodeNotRecognised() {
        MappedAuthorisationRejectedReason mappedAuthorisationRejectedReason = StripeAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason("unrecognised_code");
        assertThat(mappedAuthorisationRejectedReason.name(), is("UNCATEGORISED"));
    }

    @Test
    void shouldBeCategorisedCorrectlyIfStripeDeclineCodeIsRecognised() {
        MappedAuthorisationRejectedReason mappedAuthorisationRejectedReason = StripeAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason("authentication_required");
        assertThat(mappedAuthorisationRejectedReason.name(), is("AUTHENTICATION_REQUIRED"));
    }
}
