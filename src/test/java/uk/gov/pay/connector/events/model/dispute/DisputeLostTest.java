package uk.gov.pay.connector.events.model.dispute;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeLostEventDetails;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;

public class DisputeLostTest {

    @Test
    public void shouldSerialiseEventDetails() throws JsonProcessingException {
        DisputeLostEventDetails eventDetails = new DisputeLostEventDetails("a-gateway-account-id",
                -8000L, 6500L, 1500L);
        DisputeLost disputeLost = new DisputeLost("resource-external-id", "external-id",
                "service-id", true, eventDetails, toUTCZonedDateTime(1642579160L));

        String disputeLostJson = disputeLost.toJsonString();
        assertThat(disputeLostJson, hasJsonPath("$.event_type", equalTo("DISPUTE_LOST")));
        assertThat(disputeLostJson, hasJsonPath("$.resource_type", equalTo("dispute")));
        assertThat(disputeLostJson, hasJsonPath("$.resource_external_id", equalTo("resource-external-id")));
        assertThat(disputeLostJson, hasJsonPath("$.timestamp", equalTo("2022-01-19T07:59:20.000000Z")));
        assertThat(disputeLostJson, hasJsonPath("$.live", equalTo(true)));
        assertThat(disputeLostJson, hasJsonPath("$.parent_resource_external_id", equalTo("external-id")));

        assertThat(disputeLostJson, hasJsonPath("$.event_details.gateway_account_id", equalTo("a-gateway-account-id")));
        assertThat(disputeLostJson, hasJsonPath("$.event_details.net_amount", equalTo(-8000)));
        assertThat(disputeLostJson, hasJsonPath("$.event_details.amount", equalTo(6500)));
        assertThat(disputeLostJson, hasJsonPath("$.event_details.fee", equalTo(1500)));
    }
}