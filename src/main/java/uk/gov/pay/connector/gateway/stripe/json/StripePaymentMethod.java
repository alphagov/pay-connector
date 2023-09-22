package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gateway.stripe.json.StripeObjectWithId;

import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripePaymentMethod implements StripeObjectWithId {
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
