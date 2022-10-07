package uk.gov.pay.connector.events.model.charge;

import org.junit.Test;

import java.time.Instant;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class PaymentIncludedInPayoutTest {

    @Test
    public void serializesEventDetailsGivenPaymentIncludedInPayoutTest() throws Exception {
        var paymentExternalId = "payment-id";
        var gatewayPayoutId = "payout-id";
        String eventDateStr = "2020-05-10T10:30:00.000000Z";
        var event = new PaymentIncludedInPayout(paymentExternalId, gatewayPayoutId, Instant.parse(eventDateStr));
        
        var json = event.toJsonString();

        assertThat(json, hasJsonPath("$.event_type", equalTo("PAYMENT_INCLUDED_IN_PAYOUT")));
        assertThat(json, hasJsonPath("$.resource_type", equalTo("payment")));
        assertThat(json, hasJsonPath("$.resource_external_id", equalTo(paymentExternalId)));
        assertThat(json, hasJsonPath("$.timestamp", equalTo(eventDateStr)));
        assertThat(json, hasNoJsonPath("$.live"));

        assertThat(json, hasJsonPath("$.event_details.gateway_payout_id", equalTo(gatewayPayoutId)));
    }
}
