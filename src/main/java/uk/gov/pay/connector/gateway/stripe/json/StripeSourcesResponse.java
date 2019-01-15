package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeSourcesResponse {

    private List<String> threeDSecureRequiredOptions = ImmutableList.of("required", "recommended", "optional");

    @JsonProperty("id")
    private String id;

    @JsonProperty("card")
    private Card card;

    public String getId() {
        return id;
    }

    public String getTransactionId() {
        return id;
    }

    public boolean require3ds() {
        return card != null && threeDSecureRequiredOptions.contains(card.getThreeDSecure());
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
