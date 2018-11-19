package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeSourcesResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("card")
    private Card card;

    public String getId() {
        return id;
    }

    public boolean require3ds() {
        return card != null && "required".equals(card.getThreeDSecure());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Card {
        @JsonProperty("three_d_secure")
        private String threeDSecure;

        public String getThreeDSecure() {
            return threeDSecure;
        }
    }
}
