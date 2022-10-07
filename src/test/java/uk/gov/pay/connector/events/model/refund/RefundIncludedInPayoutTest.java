package uk.gov.pay.connector.events.model.refund;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

class RefundIncludedInPayoutTest {

    @Test
    void serializesEventDetails() throws Exception {
        var refundExternalId = "refund-id";
        var gatewayPayoutId = "payout-id";
        String eventDateStr = "2020-05-10T10:30:00.000000Z";
        var event = new RefundIncludedInPayout(refundExternalId, gatewayPayoutId, Instant.parse(eventDateStr));

        var json = event.toJsonString();

        assertThat(json, hasJsonPath("$.event_type", equalTo("REFUND_INCLUDED_IN_PAYOUT")));
        assertThat(json, hasJsonPath("$.resource_type", equalTo("refund")));
        assertThat(json, hasJsonPath("$.resource_external_id", equalTo(refundExternalId)));
        assertThat(json, hasJsonPath("$.timestamp", equalTo(eventDateStr)));
        assertThat(json, hasNoJsonPath("$.live"));

        assertThat(json, hasJsonPath("$.event_details.gateway_payout_id", equalTo(gatewayPayoutId)));
    }
}
