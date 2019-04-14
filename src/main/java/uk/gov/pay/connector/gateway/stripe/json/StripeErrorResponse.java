package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

import java.util.StringJoiner;

/** Example response from Stripe:
{
  "error": {
    "message": "Invalid API Key provided: sk_test_************S8wL",
    "type": "invalid_request_error"
  }
}
 
 For more detail see https://stripe.com/docs/api/errors?lang=java
 **/
@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeErrorResponse {

    @JsonProperty("error")
    private Error error;

    public Error getError() {
        return error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public class Error {

        @JsonProperty("code")
        private String code;
        @JsonProperty("message")
        private String message;

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
    
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "Stripe capture response (", ")");
        if (StringUtils.isNotBlank(error.getCode())) {
            joiner.add("error code: " + error.getCode());
        }
        if (StringUtils.isNotBlank(error.getMessage())) {
            joiner.add("error: " + error.getMessage());
        }
        return joiner.toString();
    }
}
