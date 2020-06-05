package uk.gov.pay.connector.events.model.payout;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static java.time.ZonedDateTime.parse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class PayoutFailedTest {
    @Test
    public void shouldSerializePayoutFailedEventWithCorrectEventDetails() throws JsonProcessingException {
        StripePayout payout = new StripePayout("po_123", "pending", "account_closed",
                "The bank account has been closed", "ba_1GkZtqDv3CZEaFO2CQhLrluk");
        String payoutEventJson = PayoutFailed.from(parse("2020-05-13T18:50:00Z"), payout).toJsonString();

        assertThat(payoutEventJson, hasJsonPath("$.event_type", equalTo("PAYOUT_FAILED")));
        assertThat(payoutEventJson, hasJsonPath("$.resource_type", equalTo("payout")));
        assertThat(payoutEventJson, hasJsonPath("$.resource_external_id", equalTo(payout.getId())));
        assertThat(payoutEventJson, hasJsonPath("$.timestamp", equalTo("2020-05-13T18:50:00.000000Z")));

        assertThat(payoutEventJson, hasJsonPath("$.event_details.gateway_status", equalTo(payout.getStatus())));
        assertThat(payoutEventJson, hasJsonPath("$.event_details.failure_code",
                equalTo("account_closed")));
        assertThat(payoutEventJson, hasJsonPath("$.event_details.failure_message",
                equalTo("The bank account has been closed")));
        assertThat(payoutEventJson, hasJsonPath("$.event_details.failure_balance_transaction",
                equalTo("ba_1GkZtqDv3CZEaFO2CQhLrluk")));
    }
}
