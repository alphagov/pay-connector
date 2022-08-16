package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeSearchTransfersResponse {

    @JsonProperty("has_more")
    private boolean hasMore;
    @JsonProperty("data")
    private List<StripeTransfer> transfers;

    public boolean hasMore() {
        return hasMore;
    }

    public List<StripeTransfer> getTransfers() {
        return transfers;
    }
}
