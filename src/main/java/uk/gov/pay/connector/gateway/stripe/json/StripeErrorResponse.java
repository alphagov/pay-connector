package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.gateway.stripe.util.PaymentIntentStringifier;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.YearMonth;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Example response from Stripe:
 * {
 * "error": {
 * "message": "Invalid API Key provided: sk_test_************S8wL",
 * "type": "invalid_request_error"
 * }
 * }
 * <p>
 * For more detail see https://stripe.com/docs/api/errors?lang=java
 **/
@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeErrorResponse {
    
    @JsonProperty("error")
    private Error error;

    public Error getError() {
        return error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Error {

        @JsonProperty("charge")
        private String charge;
        @JsonProperty("type")
        private String type;
        @JsonProperty("code")
        private String code;
        @JsonProperty("message")
        private String message;
        @JsonProperty("payment_intent")
        @JsonDeserialize(using = PaymentIntentDeserializer.class)
        private PaymentIntent paymentIntent;

        public String getType() {
            return type;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public String getCharge() {
            return charge;
        }

        public Optional<PaymentIntent> getPaymentIntent() {
            return Optional.ofNullable(paymentIntent);
        }
        
        public Optional<CardExpiryDate> getCardExpiryDate() {
            return Optional.ofNullable(paymentIntent)
                    .flatMap(x -> {
                        PaymentMethod.Card card = x.getPaymentMethodObject().getCard();
                        return Optional.of(CardExpiryDate.valueOf(YearMonth.of(card.getExpYear().intValue(), card.getExpMonth().intValue())));
                    });
        }

        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner(", ");
            if (StringUtils.isNotBlank(charge)) {
                joiner.add("stripe charge: " + getCharge());
            }
            if (StringUtils.isNotBlank(type)) {
                joiner.add("type: " + getType());
            }
            if (StringUtils.isNotBlank(code)) {
                joiner.add("code: " + getCode());
            }
            if (StringUtils.isNotBlank(message)) {
                joiner.add("message: " + getMessage());
            }
            getPaymentIntent()
                    .ifPresent(paymentIntent -> {
                        joiner.add("payment intent: " + this.paymentIntent.getId());
                        if (paymentIntent.getCharges() != null && !paymentIntent.getCharges().getData().isEmpty() &&
                                paymentIntent.getCharges().getData().get(0).getOutcome() != null) {
                            PaymentIntentStringifier.appendOutcomeLogs(paymentIntent.getCharges().getData().get(0).getOutcome(), joiner);
                        }
                    });
            return joiner.toString();
        }
    }

    @Override
    public String toString() {
        return error.toString();
    }
}
