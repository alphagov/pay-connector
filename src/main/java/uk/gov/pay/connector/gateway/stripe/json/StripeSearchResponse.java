package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeSearchResponse {

    @JsonProperty("has_more")
    private boolean hasMore;
    @JsonProperty("data")
    private List<StripePaymentIntent> paymentIntents;

    public boolean hasMore() {
        return hasMore;
    }

    public List<StripePaymentIntent> getPaymentIntents() {
        return paymentIntents;
    }
}
