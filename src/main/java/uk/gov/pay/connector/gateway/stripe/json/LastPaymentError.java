package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LastPaymentError {
    @JsonProperty("decline_code")
    private String declineCode;

    @JsonProperty("type")
    private String type;

    public String getDeclineCode() {
        return declineCode;
    }

    public String getType() {
        return type;
    }
}
