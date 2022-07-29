package uk.gov.pay.connector.gateway.stripe;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.stripe.StripeFullTestCardNumbers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class StripeFullTestCardNumbersTest {
    private String firstSixDigits = "400000";

    @Test
    void shouldReturnLosingEvidenceText() {
        var evidenceText = StripeFullTestCardNumbers.getSubmitTestDisputeEvidenceText(firstSixDigits, "0259", "A cardholder name");
        assertThat(evidenceText.isPresent(), is(true));
        assertThat(evidenceText.get(), is("losing_evidence"));
    }

    @Test
    void shouldReturnWinningEvidenceText() {
        var evidenceText = StripeFullTestCardNumbers.getSubmitTestDisputeEvidenceText(firstSixDigits, "2685", "A cardholder name");
        assertThat(evidenceText.isPresent(), is(true));
        assertThat(evidenceText.get(), is("winning_evidence"));
    }

    @Test
    void shouldHandleNullCardholderName() {
        var evidenceText = StripeFullTestCardNumbers.getSubmitTestDisputeEvidenceText(firstSixDigits, "2685", null);
        assertThat(evidenceText.isPresent(), is(true));
        assertThat(evidenceText.get(), is("winning_evidence"));
    }

    @Test
    void shouldReturnEmpty_whenLastFourDigitsDoNotMatch() {
        var evidenceText = StripeFullTestCardNumbers.getSubmitTestDisputeEvidenceText(firstSixDigits, "1976", "A cardholder name");
        assertThat(evidenceText.isPresent(), is(false));
    }
    
    @Test
    void shouldReturnEmpty_whenCardholderNameMatchesSkipEvidenceReservedValue() {
        var evidenceText = StripeFullTestCardNumbers.getSubmitTestDisputeEvidenceText(firstSixDigits, "2685", "skip_evidence");
        assertThat(evidenceText.isPresent(), is(false));
    }
}
