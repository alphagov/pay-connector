package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.service.payments.commons.api.json.MicrosecondPrecisionDateTimeSerializer.MICROSECOND_FORMATTER;

public class UserEmailCollectedTest {

    @Test
    public void serializesEventDetailsForChargeAndEventDate() throws JsonProcessingException {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        ZonedDateTime eventDate = ZonedDateTime.now(UTC);
        String actual = UserEmailCollected.from(chargeEntity, eventDate).toJsonString();

        assertThat(actual, hasJsonPath("$.timestamp", equalTo(MICROSECOND_FORMATTER.format(eventDate))));
        assertThat(actual, hasJsonPath("$.event_type", equalTo("USER_EMAIL_COLLECTED")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.event_details.email", equalTo(chargeEntity.getEmail())));
    }
}
