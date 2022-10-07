package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;

import java.time.Instant;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;

class PaymentDisputedTest {

    @Test
    void shouldBuildAndSerialiseEventDetailsCorrectly() throws JsonProcessingException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("payment-external-id")
                .withGatewayAccountId(1234L)
                .withGatewayTransactionId("payment-intent-id")
                .withServiceId("service-id")
                .isLive(true)
                .build();
        String timestampString = "2022-01-19T07:59:20.000000Z";
        Instant timestamp = Instant.parse(timestampString);
        
        var paymentDisputed = PaymentDisputed.from(transaction, timestamp);
        
        String paymentDisputedJson = paymentDisputed.toJsonString();
        assertThat(paymentDisputedJson, hasJsonPath("$.event_type", equalTo("PAYMENT_DISPUTED")));
        assertThat(paymentDisputedJson, hasJsonPath("$.resource_type", IsEqual.equalTo("payment")));
        assertThat(paymentDisputedJson, hasJsonPath("$.service_id", IsEqual.equalTo("service-id")));
        assertThat(paymentDisputedJson, hasJsonPath("$.resource_external_id", IsEqual.equalTo("payment-external-id")));
        assertThat(paymentDisputedJson, hasJsonPath("$.timestamp", IsEqual.equalTo("2022-01-19T07:59:20.000000Z")));
        assertThat(paymentDisputedJson, hasJsonPath("$.live", IsEqual.equalTo(true)));

        assertThat(paymentDisputedJson, hasJsonPath("$.event_details.disputed", IsEqual.equalTo(true)));
    }
}
