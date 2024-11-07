package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

import java.time.Instant;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;

class GatewayDoesNotRequire3DsAuthorisationTest {
    private final ChargeEntity chargeEntity = aValidChargeEntity().build();

    @Test
    void serializesEventDetailsGivenChargeEvent() throws JsonProcessingException {
        Instant eventDate = Instant.now();
        String actual = GatewayDoesNotRequire3dsAuthorisation.from(chargeEntity, eventDate).toJsonString();

        assertThat(actual, hasJsonPath("$.event_type", equalTo("GATEWAY_DOES_NOT_REQUIRE_3DS_AUTHORISATION")));
        assertThat(actual, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(actual, hasJsonPath("$.resource_external_id", equalTo(chargeEntity.getExternalId())));
        assertThat(actual, hasJsonPath("$.service_id", equalTo(chargeEntity.getServiceId())));

        assertThat(actual, hasJsonPath("$.event_details.requires_3ds", equalTo(false)));
    }
}
