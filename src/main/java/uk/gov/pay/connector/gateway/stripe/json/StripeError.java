package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Example response from Stripe:
{
  "error": {
    "message": "Invalid API Key provided: sk_test_************S8wL",
    "type": "invalid_request_error"
  }
}
 **/
@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeError {

    @JsonProperty("error")
    private Error error;

    public Error getError() {
        return error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public class Error {

        @JsonProperty("message")
        private String message;

        @JsonProperty("type")
        private String type;

        public String getMessage() {
            return message;
        }

        public String getType() {
            return type;
        }
    }
}
