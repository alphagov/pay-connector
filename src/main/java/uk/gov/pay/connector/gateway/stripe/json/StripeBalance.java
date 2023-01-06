package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeBalance {

    @JsonProperty("available")
    private List<Available> available;

    public List<Available> getAvailable() {
        return available;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Available {
        
        @JsonProperty("amount")
        private Integer amount;
        
        @JsonProperty("currency")
        private String currency;

        public Integer getAmount() {
            return amount;
        }

        public String getCurrency() {
            return currency;
        }
    }
}
