package uk.gov.pay.connector.events.model.dispute;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.queue.tasks.dispute.BalanceTransaction;
import uk.gov.pay.connector.queue.tasks.dispute.EvidenceDetails;
import uk.gov.pay.connector.queue.tasks.dispute.StripeDisputeData;

import java.util.List;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.events.model.dispute.DisputeCreated.from;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;

class DisputeCreatedTest {

    @Test
    public void shouldBuildAndSerialiseEventDetailsCorrectly() throws JsonProcessingException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("payment-external-id")
                .withGatewayAccountId(1234L)
                .withGatewayTransactionId("payment-intent-id")
                .withServiceId("service-id")
                .isLive(true)
                .build();
        BalanceTransaction balanceTransaction = new BalanceTransaction(6500L, 1500L, -8000L);
        EvidenceDetails evidenceDetails = new EvidenceDetails(1642679160L);
        StripeDisputeData stripeDisputeData = new StripeDisputeData("du_1LIaq8Dv3CZEaFO2MNQJK333",
                "pi_123456789", "needs_response", 6500L, "fradulent", 1642579160L, List.of(balanceTransaction), evidenceDetails);

        DisputeCreated disputeCreated = from(stripeDisputeData, transaction, toUTCZonedDateTime(1642579160L));

        String disputeCreatedJson = disputeCreated.toJsonString();
        assertThat(disputeCreatedJson, hasJsonPath("$.event_type", equalTo("DISPUTE_CREATED")));
        assertThat(disputeCreatedJson, hasJsonPath("$.resource_type", equalTo("dispute")));
        assertThat(disputeCreatedJson, hasJsonPath("$.service_id", equalTo("service-id")));
        assertThat(disputeCreatedJson, hasJsonPath("$.resource_external_id", equalTo("fca65e80d2293ee3bf158a0d12")));
        assertThat(disputeCreatedJson, hasJsonPath("$.timestamp", equalTo("2022-01-19T07:59:20.000000Z")));
        assertThat(disputeCreatedJson, hasJsonPath("$.live", equalTo(true)));
        assertThat(disputeCreatedJson, hasJsonPath("$.parent_resource_external_id", equalTo("payment-external-id")));

        assertThat(disputeCreatedJson, hasJsonPath("$.event_details.gateway_account_id", equalTo("1234")));
        assertThat(disputeCreatedJson, hasJsonPath("$.event_details.evidence_due_date", equalTo(1642679160)));
        assertThat(disputeCreatedJson, hasJsonPath("$.event_details.net_amount", equalTo(-8000)));
        assertThat(disputeCreatedJson, hasJsonPath("$.event_details.amount", equalTo(6500)));
        assertThat(disputeCreatedJson, hasJsonPath("$.event_details.fee", equalTo(1500)));
    }

    @Test
    void shouldThrowExceptionWhenMoreThanOneBalanceTransactionPresent() {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("payment-external-id")
                .withGatewayAccountId(1234L)
                .withGatewayTransactionId("payment-intent-id")
                .withServiceId("service-id")
                .isLive(true)
                .build();
        BalanceTransaction balanceTransaction = new BalanceTransaction(6500L, 1500L, -8000L);
        BalanceTransaction balanceTransaction2 = new BalanceTransaction(6500L, 1500L, 8000L);
        EvidenceDetails evidenceDetails = new EvidenceDetails(1642679160L);
        StripeDisputeData stripeDisputeData = new StripeDisputeData("du_1LIaq8Dv3CZEaFO2MNQJK333",
                "pi_123456789", "needs_response", 6500L, "fradulent", 1642579160L, List.of(balanceTransaction, balanceTransaction2), evidenceDetails);

        var thrown = assertThrows(RuntimeException.class, () ->
                from(stripeDisputeData, transaction, toUTCZonedDateTime(1642579160L)));
        assertThat(thrown.getMessage(), is("Dispute data has too many balance_transactions"));
    }
}
