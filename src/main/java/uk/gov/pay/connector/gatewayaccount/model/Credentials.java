package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class Credentials {
    
    private String accountId;

    public Credentials(@JsonProperty("account_id") String accountId) {
        this.accountId = accountId;
    }

    public Map<String, String> toMap() {
        return ImmutableMap.of("account_id", accountId);
    }
}
