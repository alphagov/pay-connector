package uk.gov.pay.connector.gateway.worldpay.util;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason;
import uk.gov.pay.connector.gateway.worldpay.utils.WorldpayAuthorisationRejectedCodeMapper;

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
