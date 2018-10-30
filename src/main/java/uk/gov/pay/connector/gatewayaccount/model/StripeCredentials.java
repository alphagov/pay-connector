package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class StripeCredentials {

    private String accountId;

    public StripeCredentials(@JsonProperty("stripe_account_id") String accountId) {
        this.accountId = accountId;
    }

    public Map<String, String> toMap() {
        return ImmutableMap.of("stripe_account_id", accountId);
    }
}
