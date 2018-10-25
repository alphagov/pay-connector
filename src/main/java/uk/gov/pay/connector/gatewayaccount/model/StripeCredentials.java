package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StripeCredentials {
    @JsonProperty("stripe_account_id")
    private String accountId;

    public StripeCredentials(@JsonProperty("stripe_account_id") String accountId) {
        this.accountId = accountId;
    }
}
