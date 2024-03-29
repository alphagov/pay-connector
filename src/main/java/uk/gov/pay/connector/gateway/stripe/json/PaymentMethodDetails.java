package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentMethodDetails {

    @JsonProperty("card")
    private Card card;

    public Card getCard() {
        return card;
    }
}
