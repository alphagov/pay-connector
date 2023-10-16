package uk.gov.pay.connector.gateway.stripe.util;

import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import uk.gov.pay.connector.gateway.model.StripeAuthorisationRejectedCodeMapper;
import uk.gov.pay.connector.gateway.stripe.json.LastPaymentError;
import uk.gov.pay.connector.gateway.stripe.json.Outcome;
import uk.gov.pay.connector.gateway.stripe.json.StripeCharge;
import uk.gov.pay.connector.gateway.stripe.json.StripePaymentIntent;

import java.util.Optional;
import java.util.StringJoiner;

public class PaymentIntentStringifier {

    @Deprecated
    public static String stringify(StripePaymentIntent paymentIntent) {

        StringJoiner joiner = new StringJoiner("");
        StringJoiner delimitedJoiner = new StringJoiner(", ");
        
        joiner.add("payment intent: " + paymentIntent.getId());
        
        if (paymentIntent.getNextAction() != null) {
            joiner.add("next action: " + paymentIntent.getNextAction());
        }

        Optional<StripeCharge> optionalStripeCharge = paymentIntent.getCharge();
        optionalStripeCharge.map(charge -> {
            joiner.add(" (");
            if (charge.getId() != null) {
                joiner.add("stripe charge: " + charge.getId());
            }

            paymentIntent.getLastPaymentError()
                    .ifPresent(lastPaymentError -> appendLastPaymentErrorLogs(lastPaymentError, delimitedJoiner));

            if (charge.getFailureCode() != null) {
                delimitedJoiner.add("code: " + charge.getFailureCode());
            }
            if (charge.getFailureMessage() != null) {
                delimitedJoiner.add("message: " + charge.getFailureMessage());
            }
            if (charge.getStatus() != null) {
                delimitedJoiner.add("status: " + charge.getStatus());
            }

            charge.getOutcome().ifPresent(outcome -> appendOutcomeLogs(outcome, delimitedJoiner));

            if (delimitedJoiner.length() > 0) {
                joiner.add(", ");
            }

            joiner.merge(delimitedJoiner);
            return joiner.add(")");
        });

        return joiner.toString();
    }    
    
    public static String stringify(PaymentIntent paymentIntent) {

        StringJoiner joiner = new StringJoiner("");
        StringJoiner delimitedJoiner = new StringJoiner(", ");
        
        joiner.add("payment intent: " + paymentIntent.getId());
        
        if (paymentIntent.getNextAction() != null) {
            joiner.add("next action: " + paymentIntent.getNextAction());
        }

        Optional<Charge> optionalStripeCharge = paymentIntent.getCharges().getData().stream().findFirst();
        optionalStripeCharge.map(charge -> {
            joiner.add(" (");
            if (charge.getId() != null) {
                joiner.add("stripe charge: " + charge.getId());
            }
            if (paymentIntent.getLastPaymentError() != null) {
                appendLastPaymentErrorLogs(paymentIntent.getLastPaymentError(), delimitedJoiner);
            }
            if (charge.getFailureCode() != null) {
                delimitedJoiner.add("code: " + charge.getFailureCode());
            }
            if (charge.getFailureMessage() != null) {
                delimitedJoiner.add("message: " + charge.getFailureMessage());
            }
            if (charge.getStatus() != null) {
                delimitedJoiner.add("status: " + charge.getStatus());
            }
            if (charge.getOutcome() != null) {
                appendOutcomeLogs(charge.getOutcome(), delimitedJoiner);
            }
            if (delimitedJoiner.length() > 0) {
                joiner.add(", ");
            }

            joiner.merge(delimitedJoiner);
            return joiner.add(")");
        });

        return joiner.toString();
    }
    
    public static void appendOutcomeLogs(Outcome outcome, StringJoiner joiner) {
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
    }    
    
    public static void appendOutcomeLogs(Charge.Outcome outcome, StringJoiner joiner) {
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
    }

    public static void appendLastPaymentErrorLogs(LastPaymentError lastPaymentError, StringJoiner joiner) {
        if (lastPaymentError.getType() != null) {
            joiner.add("type: " + lastPaymentError.getType());
        }
        if (lastPaymentError.getDeclineCode() != null) {
            joiner.add("decline code: " + lastPaymentError.getDeclineCode());
            joiner.add("Mapped rejection reason: " + StripeAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason(lastPaymentError.getDeclineCode()).name());
        }
    }

    public static void appendLastPaymentErrorLogs(StripeError stripeError, StringJoiner joiner) {
        if (stripeError.getType() != null) {
            joiner.add("type: " + stripeError.getType());
        }
        if (stripeError.getDeclineCode() != null) {
            joiner.add("decline code: " + stripeError.getDeclineCode());
            joiner.add("Mapped rejection reason: " + StripeAuthorisationRejectedCodeMapper.toMappedAuthorisationRejectionReason(stripeError.getDeclineCode()).name());
        }
    }
}
