package uk.gov.pay.connector.events.model.dispute;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.events.model.refund.RefundIncludedInPayout;

import java.time.ZonedDateTime;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

class DisputeIncludedInPayoutTest {
    
    @Test
    void serializesEventDetails() throws Exception {
        var disputeExternalId = "dispute-id";
        var gatewayPayoutId = "payout-id";
        String eventDateStr = "2020-05-10T10:30:00.000000Z";
        var event = new DisputeIncludedInPayout(disputeExternalId, gatewayPayoutId, ZonedDateTime.parse(eventDateStr));

        var json = event.toJsonString();

        assertThat(json, hasJsonPath("$.event_type", equalTo("DISPUTE_INCLUDED_IN_PAYOUT")));
        assertThat(json, hasJsonPath("$.resource_type", equalTo("dispute")));
        assertThat(json, hasJsonPath("$.resource_external_id", equalTo(disputeExternalId)));
        assertThat(json, hasJsonPath("$.timestamp", equalTo(eventDateStr)));
        assertThat(json, hasNoJsonPath("$.live"));

        assertThat(json, hasJsonPath("$.event_details.gateway_payout_id", equalTo(gatewayPayoutId)));
    }
}
