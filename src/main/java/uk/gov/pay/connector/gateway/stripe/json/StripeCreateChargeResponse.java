package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeCreateChargeResponse {
    @JsonProperty("id")
    private String id;
    @JsonProperty("status")
    private String status;

    public String getTransactionId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

}
