package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripePaymentIntent {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("charges")
    private ChargesCollection chargesCollection;

    public ChargesCollection getChargesCollection() {
        return chargesCollection;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChargesCollection {

        @JsonProperty("data")
        private List<StripeCharge> charges;

        public List<StripeCharge> getCharges() {
            return charges;
        }
    }
}
