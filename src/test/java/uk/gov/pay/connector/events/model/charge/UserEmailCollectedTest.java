package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;

import java.time.Instant;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.service.payments.commons.model.CommonDateTimeFormatters.ISO_INSTANT_MICROSECOND_PRECISION;

class UserEmailCollectedTest {

    @Test
    void serializesEventDetailsForChargeAndEventDate() throws JsonProcessingException {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        Instant eventDate = Instant.now();
        String actual = UserEmailCollected.from(chargeEntity, eventDate).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(ISO_INSTANT_MICROSECOND_PRECISION.format(eventDate))));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("USER_EMAIL_COLLECTED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.event_details.email", equalTo(chargeEntity.getEmail())));
    }
}
