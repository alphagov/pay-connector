package uk.gov.pay.connector.events.model.payout;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class PayoutCreatedTest {

    @Test
    public void serializesEventDetailsGivenPayoutCreatedEvent() throws JsonProcessingException {
        StripePayout payout = new StripePayout("po_123", 1000L, 1589395533L, 1589395500L,
                "pending", "card", "SERVICE NAME");
        String payoutCreatedJson = PayoutCreated.from(123456L, payout).toJsonString();

        assertThat(payoutCreatedJson, hasJsonPath("$.event_type", equalTo("PAYOUT_CREATED")));
        assertThat(payoutCreatedJson, hasJsonPath("$.resource_type", equalTo("payout")));
        assertThat(payoutCreatedJson, hasJsonPath("$.resource_external_id", equalTo(payout.getId())));
        assertThat(payoutCreatedJson, hasJsonPath("$.timestamp", equalTo("2020-05-13T18:45:00.000000Z")));

        assertThat(payoutCreatedJson, hasJsonPath("$.event_details.gateway_account_id", equalTo("123456")));
        assertThat(payoutCreatedJson, hasJsonPath("$.event_details.amount", equalTo(1000)));
        assertThat(payoutCreatedJson, hasJsonPath("$.event_details.estimated_arrival_date_in_bank", equalTo("2020-05-13T18:45:33.000000Z")));
        assertThat(payoutCreatedJson, hasJsonPath("$.event_details.gateway_status", equalTo(payout.getStatus())));
        assertThat(payoutCreatedJson, hasJsonPath("$.event_details.destination_type", equalTo(payout.getType())));
        assertThat(payoutCreatedJson, hasJsonPath("$.event_details.statement_descriptor", equalTo(payout.getStatementDescriptor())));
    }
}
