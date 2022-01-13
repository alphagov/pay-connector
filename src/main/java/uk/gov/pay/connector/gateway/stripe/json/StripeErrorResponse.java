package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

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
        private StripePaymentIntent stripePaymentIntent;

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
        
        public StripePaymentIntent getStripePaymentIntent() {
            return stripePaymentIntent;
        }

        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner(", ");
            if (StringUtils.isNotBlank(type)) {
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
            if (stripePaymentIntent != null) {
                joiner.add("payment intent: " + stripePaymentIntent.getId());
            }
            return joiner.toString();
        }
    }

    @Override
    public String toString() {
        return error.toString();
    }
}
