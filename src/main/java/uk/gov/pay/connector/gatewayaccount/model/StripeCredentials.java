package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class StripeCredentials {

    public static final String STRIPE_ACCOUNT_ID_KEY = "stripe_account_id";
    private String accountId;

    public StripeCredentials(@JsonProperty(STRIPE_ACCOUNT_ID_KEY) String accountId) {
        this.accountId = accountId;
    }

    public Map<String, String> toMap() {
        return ImmutableMap.of(STRIPE_ACCOUNT_ID_KEY, accountId);
    }
}
