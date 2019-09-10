package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripePaymentIntent {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("charges")
    private ChargesCollection chargesCollection;
    
    @JsonProperty("amount_capturable")
    private Long amountCapturable;

    public ChargesCollection getChargesCollection() {
        return chargesCollection;
    }

    public String getId() {
        return id;
    }

    public Long getAmountCapturable() {
        return amountCapturable;
    }
    
    public Optional<StripeCharge> getCharge() {
        return chargesCollection.getCharges().stream()
                .findFirst();
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
