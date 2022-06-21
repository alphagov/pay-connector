package uk.gov.pay.connector.events.model.dispute;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeEvidenceSubmittedEventDetails;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;

public class DisputeEvidenceSubmittedTest {

    @Test
    public void shouldSerialiseEventDetails() throws JsonProcessingException {
        DisputeEvidenceSubmittedEventDetails eventDetails = new DisputeEvidenceSubmittedEventDetails("a-gateway-account-id");
        DisputeEvidenceSubmitted disputeEvidenceSubmitted = new DisputeEvidenceSubmitted("resource-external-id",
                "external-id", "service-id", true, eventDetails, toUTCZonedDateTime(1642579160L));

        String disputeEvidenceSubmittedJson = disputeEvidenceSubmitted.toJsonString();
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.event_type", equalTo("DISPUTE_EVIDENCE_SUBMITTED")));
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.resource_type", equalTo("dispute")));
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.resource_external_id", equalTo("resource-external-id")));
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.timestamp", equalTo("2022-01-19T07:59:20.000000Z")));
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.live", equalTo(true)));
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.parent_resource_external_id", equalTo("external-id")));

        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.event_details.gateway_account_id", equalTo("a-gateway-account-id")));
    }

}