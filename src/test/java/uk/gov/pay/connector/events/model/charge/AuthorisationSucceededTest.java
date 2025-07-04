package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class AuthorisationSucceededTest {

    private static final String SERVICE_ID = "service";
    private static final long GATEWAY_ACCOUNT_ID = 1L;
    private static final String RESOURCE_EXTERNAL_ID = "resource";
    private static final String TIMESTAMP = "2024-04-01T10:11:12.123456Z";

    @Test
    void serializes() throws JsonProcessingException {
        var event = new AuthorisationSucceeded(
                SERVICE_ID,
                true,
                GATEWAY_ACCOUNT_ID,
                RESOURCE_EXTERNAL_ID,
                Instant.parse(TIMESTAMP));

        String json = event.toJsonString();

        assertThat(json, hasJsonPath("$.timestamp", equalTo(TIMESTAMP)));
        assertThat(json, hasJsonPath("$.event_type", equalTo("AUTHORISATION_SUCCEEDED")));
        assertThat(json, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(json, hasJsonPath("$.live", equalTo(true)));
        assertThat(json, hasJsonPath("$.service_id", equalTo(SERVICE_ID)));
        assertThat(json, hasJsonPath("$.gateway_account_id", equalTo((int) GATEWAY_ACCOUNT_ID)));
        assertThat(json, hasJsonPath("$.resource_external_id", equalTo(RESOURCE_EXTERNAL_ID)));
    }

}
