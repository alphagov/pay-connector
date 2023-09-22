package uk.gov.pay.connector.events.model.dispute;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.gateway.stripe.json.StripeDisputeData;

import java.time.Instant;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static uk.gov.pay.connector.events.model.dispute.DisputeEvidenceSubmitted.from;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;

class DisputeEvidenceSubmittedTest {

    @Test
    void shouldSerialiseEventDetails() throws JsonProcessingException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("payment-external-id")
                .withGatewayAccountId(1234L)
                .withGatewayTransactionId("payment-intent-id")
                .withServiceId("service-id")
                .isLive(true)
                .build();
        StripeDisputeData stripeDisputeData = new StripeDisputeData("du_1LIaq8Dv3CZEaFO2MNQJK333",
                "pi_123456789", "under_review", 6500L, "fradulent",
                1642579160L, null, null, null, true);

        String disputeExternalId = "fca65e80d2293ee3bf158a0d12";
        DisputeEvidenceSubmitted disputeEvidenceSubmitted = from(disputeExternalId, Instant.ofEpochSecond(1642579160L), transaction);

        String disputeEvidenceSubmittedJson = disputeEvidenceSubmitted.toJsonString();
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.event_type", equalTo("DISPUTE_EVIDENCE_SUBMITTED")));
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.resource_type", equalTo("dispute")));
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.service_id", equalTo("service-id")));
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.resource_external_id", equalTo(disputeExternalId)));
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.timestamp", equalTo("2022-01-19T07:59:20.000000Z")));
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.live", equalTo(true)));
        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.parent_resource_external_id", equalTo("payment-external-id")));

        assertThat(disputeEvidenceSubmittedJson, hasJsonPath("$.event_details.gateway_account_id", equalTo("1234")));
    }

}
