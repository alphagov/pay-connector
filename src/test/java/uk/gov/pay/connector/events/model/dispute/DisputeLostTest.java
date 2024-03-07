package uk.gov.pay.connector.events.model.dispute;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.gateway.stripe.json.StripeDisputeData;
import uk.gov.pay.connector.queue.tasks.dispute.BalanceTransaction;

import java.time.Instant;
import java.util.List;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.pay.connector.events.model.dispute.DisputeLost.from;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;

class DisputeLostTest {

    @Test
    void shouldBuildAndSerialiseEventDetailsCorrectly_whenRechargedToServiceTrue() throws JsonProcessingException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("payment-external-id")
                .withGatewayAccountId(1234L)
                .withGatewayTransactionId("payment-intent-id")
                .withServiceId("service-id")
                .isLive(true)
                .build();
        BalanceTransaction balanceTransaction = new BalanceTransaction(6500L, 1500L, -8000L);
        StripeDisputeData stripeDisputeData = new StripeDisputeData("du_1LIaq8Dv3CZEaFO2MNQJK333",
                "pi_123456789", "lost", 6500L, "fradulent", 1642579160L,
                List.of(balanceTransaction), null, null, true);
        String disputeExternalId = "fca65e80d2293ee3bf158a0d12";

        DisputeLost disputeLost = from(disputeExternalId, stripeDisputeData, Instant.ofEpochSecond(1642579160L), transaction, true, balanceTransaction.getNetAmount(), Math.abs(balanceTransaction.getFee()));

        String disputeLostJson = disputeLost.toJsonString();
        assertThat(disputeLostJson, hasJsonPath("$.event_type", equalTo("DISPUTE_LOST")));
        assertThat(disputeLostJson, hasJsonPath("$.resource_type", equalTo("dispute")));
        assertThat(disputeLostJson, hasJsonPath("$.service_id", equalTo("service-id")));
        assertThat(disputeLostJson, hasJsonPath("$.resource_external_id", equalTo(disputeExternalId)));
        assertThat(disputeLostJson, hasJsonPath("$.timestamp", equalTo("2022-01-19T07:59:20.000000Z")));
        assertThat(disputeLostJson, hasJsonPath("$.live", equalTo(true)));
        assertThat(disputeLostJson, hasJsonPath("$.parent_resource_external_id", equalTo("payment-external-id")));

        assertThat(disputeLostJson, hasJsonPath("$.event_details.gateway_account_id", equalTo("1234")));
        assertThat(disputeLostJson, hasJsonPath("$.event_details.net_amount", equalTo(-8000)));
        assertThat(disputeLostJson, hasJsonPath("$.event_details.amount", equalTo(6500)));
        assertThat(disputeLostJson, hasJsonPath("$.event_details.fee", equalTo(1500)));
    }

    @Test
    void shouldBuildAndSerialiseEventDetailsCorrectly_whenRechargedToServiceFalse() throws JsonProcessingException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("payment-external-id")
                .withGatewayAccountId(1234L)
                .withGatewayTransactionId("payment-intent-id")
                .withServiceId("service-id")
                .isLive(true)
                .build();
        BalanceTransaction balanceTransaction = new BalanceTransaction(6500L, 1500L, -8000L);
        StripeDisputeData stripeDisputeData = new StripeDisputeData("du_1LIaq8Dv3CZEaFO2MNQJK333",
                "pi_123456789", "lost", 6500L, "fradulent", 1642579160L,
                List.of(balanceTransaction), null, null, false);
        String disputeExternalId = "fca65e80d2293ee3bf158a0d12";

        DisputeLost disputeLost = from(disputeExternalId, stripeDisputeData, Instant.ofEpochSecond(1642579160L), transaction, false, balanceTransaction.getNetAmount(), Math.abs(balanceTransaction.getFee()));

        String disputeLostJson = disputeLost.toJsonString();
        assertThat(disputeLostJson, hasJsonPath("$.event_type", equalTo("DISPUTE_LOST")));
        assertThat(disputeLostJson, hasJsonPath("$.resource_type", equalTo("dispute")));
        assertThat(disputeLostJson, hasJsonPath("$.service_id", equalTo("service-id")));
        assertThat(disputeLostJson, hasJsonPath("$.resource_external_id", equalTo(disputeExternalId)));
        assertThat(disputeLostJson, hasJsonPath("$.timestamp", equalTo("2022-01-19T07:59:20.000000Z")));
        assertThat(disputeLostJson, hasJsonPath("$.live", equalTo(true)));
        assertThat(disputeLostJson, hasJsonPath("$.parent_resource_external_id", equalTo("payment-external-id")));

        assertThat(disputeLostJson, hasJsonPath("$.event_details.gateway_account_id", equalTo("1234")));
        assertThat(disputeLostJson, hasJsonPath("$.event_details.amount", equalTo(6500)));
        assertThat(disputeLostJson, hasNoJsonPath("$.event_details.net_amount"));
        assertThat(disputeLostJson, hasNoJsonPath("$.event_details.fee"));
    }
}
