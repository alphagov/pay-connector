package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Card {

    @JsonProperty("three_d_secure")
    private ThreeDSecure threeDSecure;

    public ThreeDSecure getThreeDSecure() {
        return threeDSecure;
    }
}
