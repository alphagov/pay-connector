package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripePaymentMethodResponse {
    @JsonProperty("id")
    private String id;
    
    private StripeCard card;

    public String getId() {
        return id;
    }

    public Optional<StripeCard> getCard() {
        return Optional.ofNullable(card);
    }
}
