package uk.gov.pay.connector.gateway.stripe.util;

import uk.gov.pay.connector.gateway.stripe.json.Outcome;
import uk.gov.pay.connector.gateway.stripe.json.StripeCharge;
import uk.gov.pay.connector.gateway.stripe.json.StripePaymentIntent;

import java.util.Optional;
import java.util.StringJoiner;

public class PaymentIntentStringifier {

    public static String stringify(StripePaymentIntent paymentIntent) {

        StringJoiner joiner = new StringJoiner("");
        StringJoiner delimitedJoiner = new StringJoiner(", ");

        joiner.add("Stripe authorisation response - ");
        joiner.add("payment intent: " + paymentIntent.getId());

        Optional<StripeCharge> optionalStripeCharge = paymentIntent.getCharge();
        optionalStripeCharge.map(charge -> {
            joiner.add(" (");
            if (charge.getId() != null) {
                joiner.add("stripe charge: " + charge.getId());
            }
            paymentIntent.getLastPaymentError()
                    .map(lastPaymentError -> {
                        if (lastPaymentError.getType() != null) {
                            delimitedJoiner.add("type: " + lastPaymentError.getType());
                        }
                        if (lastPaymentError.getDeclineCode() != null) {
                            delimitedJoiner.add("decline code: " + lastPaymentError.getDeclineCode());
                        }
                        return delimitedJoiner;
                    });
            if (charge.getFailureCode() != null) {
                delimitedJoiner.add("code: " + charge.getFailureCode());
            }
            if (charge.getFailureMessage() != null) {
                delimitedJoiner.add("message: " + charge.getFailureMessage());
            }
            if (charge.getStatus() != null) {
                delimitedJoiner.add("status: " + charge.getStatus());
            }

            charge.getOutcome()
                    .map(outcome -> appendOutcomeLogs(outcome, delimitedJoiner));

            if (delimitedJoiner.length() > 0) {
                joiner.add(", ");
            }

            joiner.merge(delimitedJoiner);
            return joiner.add(")");
        });

        return joiner.toString();
    }

    public static StringJoiner appendOutcomeLogs(Outcome outcome, StringJoiner joiner) {
        if (outcome.getNetworkStatus() != null) {
            joiner.add("outcome.network_status: " + outcome.getNetworkStatus());
        }
        if (outcome.getReason() != null) {
            joiner.add("outcome.reason: " + outcome.getReason());
        }
        if (outcome.getRiskLevel() != null) {
            joiner.add("outcome.risk_level: " + outcome.getRiskLevel());
        }
        if (outcome.getSellerMessage() != null) {
            joiner.add("outcome.seller_message: " + outcome.getSellerMessage());
        }
        if (outcome.getType() != null) {
            joiner.add("outcome.type: " + outcome.getType());
        }
        return joiner;
    }
}
