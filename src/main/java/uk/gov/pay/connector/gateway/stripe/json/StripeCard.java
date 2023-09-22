package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeCard {
    
    @JsonProperty("exp_month")
    private Integer cardExpiryMonth;

    @JsonProperty("exp_year")
    private Integer cardExpiryYear;

    public Integer getCardExpiryMonth() {
        return cardExpiryMonth;
    }

    public Integer getCardExpiryYear() {
        return cardExpiryYear;
    }
}
