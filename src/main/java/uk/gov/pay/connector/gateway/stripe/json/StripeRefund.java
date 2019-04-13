package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeRefund {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("charge")
    private StripeCharge charge;
    
    public boolean isPlatformRefund() {
        return Optional.ofNullable(charge)
                .map(StripeCharge::isPlatformCharge)
                .orElse(false);
    }

    public String getId() {
        return id;
    }
}
