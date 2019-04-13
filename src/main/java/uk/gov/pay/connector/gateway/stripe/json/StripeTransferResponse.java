package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeTransferResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("amount")
    private String amount;

    @JsonProperty("destination")
    private String destinationStripeAccountId;

    @JsonProperty("transfer_group")
    private String stripeTransferGroup;

    public String getId() {
        return id;
    }

    public String getAmount() {
        return amount;
    }

    public String getDestinationStripeAccountId() {
        return destinationStripeAccountId;
    }

    public String getStripeTransferGroup() {
        return stripeTransferGroup;
    }
}
