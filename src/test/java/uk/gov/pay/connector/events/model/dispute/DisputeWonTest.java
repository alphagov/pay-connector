package uk.gov.pay.connector.events.model.dispute;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeWonEventDetails;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;

public class DisputeWonTest {

    @Test
    public void shouldSerialiseEventDetails() throws JsonProcessingException {
        DisputeWonEventDetails eventDetails = new DisputeWonEventDetails("a-gateway-account-id");
        DisputeWon disputeWon = new DisputeWon("resource-external-id", "external-id",
                "service-id", true, eventDetails, toUTCZonedDateTime(1642579160L));

        String disputeWonJson = disputeWon.toJsonString();
        assertThat(disputeWonJson, hasJsonPath("$.event_type", equalTo("DISPUTE_WON")));
        assertThat(disputeWonJson, hasJsonPath("$.resource_type", equalTo("dispute")));
        assertThat(disputeWonJson, hasJsonPath("$.resource_external_id", equalTo("resource-external-id")));
        assertThat(disputeWonJson, hasJsonPath("$.timestamp", equalTo("2022-01-19T07:59:20.000000Z")));
        assertThat(disputeWonJson, hasJsonPath("$.live", equalTo(true)));
        assertThat(disputeWonJson, hasJsonPath("$.parent_resource_external_id", equalTo("external-id")));

        assertThat(disputeWonJson, hasJsonPath("$.event_details.gateway_account_id", equalTo("a-gateway-account-id")));
    }
}