package uk.gov.pay.connector.gateway.stripe;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.stripe.StripeFullTestCardNumbers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class StripeFullTestCardNumbersTest {
    private String firstSixDigits = "400000";

    @Test
    void shouldReturnLosingEvidenceText() {
        var evidenceText = StripeFullTestCardNumbers.getSubmitTestDisputeEvidenceText(firstSixDigits, "0259");
        assertThat(evidenceText.isPresent(), is(true));
        assertThat(evidenceText.get(), is("losing_evidence"));
    }

    @Test
    void shouldReturnWinningEvidenceText() {
        var evidenceText = StripeFullTestCardNumbers.getSubmitTestDisputeEvidenceText(firstSixDigits, "2685");
        assertThat(evidenceText.isPresent(), is(true));
        assertThat(evidenceText.get(), is("winning_evidence"));
    }

    @Test
    void shouldReturnEmpty_whenLastFourDigitsDoNotMatch() {
        var evidenceText = StripeFullTestCardNumbers.getSubmitTestDisputeEvidenceText(firstSixDigits, "1976");
        assertThat(evidenceText.isPresent(), is(false));
    }
}
