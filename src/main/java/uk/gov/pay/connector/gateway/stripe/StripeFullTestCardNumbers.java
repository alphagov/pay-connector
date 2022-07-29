package uk.gov.pay.connector.gateway.stripe;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class StripeFullTestCardNumbers {

    private static final String STRIPE_LOSING_EVIDENCE_CARD_NUMBER = "4000000000000259";
    private static final String STRIPE_WINNING_EVIDENCE_CARD_NUMBER = "4000000000002685";
    private static final String SKIP_SUBMITTING_EVIDENCE_CARDHOLDER_NAME = "skip_evidence";

    public static Optional<String> getSubmitTestDisputeEvidenceText(String firstSixDigits, String lastFourDigits, String cardholderName) {
        if (SKIP_SUBMITTING_EVIDENCE_CARDHOLDER_NAME.equals(cardholderName)) {
            return Optional.empty();
        }
        if (StringUtils.left(STRIPE_LOSING_EVIDENCE_CARD_NUMBER, 6).equals(firstSixDigits)) {
            if (StringUtils.right(STRIPE_LOSING_EVIDENCE_CARD_NUMBER, 4).equals(lastFourDigits)) {
                return Optional.of("losing_evidence");
            } else if (StringUtils.right(STRIPE_WINNING_EVIDENCE_CARD_NUMBER, 4).equals(lastFourDigits)) {
                return Optional.of("winning_evidence");
            }
        }
        return Optional.empty();
    }
}
