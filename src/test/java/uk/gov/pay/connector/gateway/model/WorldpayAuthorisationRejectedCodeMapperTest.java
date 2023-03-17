package uk.gov.pay.connector.gateway.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class WorldpayAuthorisationRejectedCodeMapperTest {

    @Test
    void shouldBeUncategorisedIfWorldpayDeclineCodeNotRecognised() {
        MappedAuthorisationRejectedReason mappedAuthorisationRejectedReason = WorldpayAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason("9457");
        assertThat(mappedAuthorisationRejectedReason.name(), is("UNCATEGORISED"));
    }

    @Test
    void shouldBeCategorisedCorrectlyIfWorldpayDeclineCodeIsRecognised() {
        MappedAuthorisationRejectedReason mappedAuthorisationRejectedReason = WorldpayAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason("5");
        assertThat(mappedAuthorisationRejectedReason.name(), is("DO_NOT_HONOUR"));
    }
}
