package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class GatewayAccount {
    public static final String CREDENTIALS_MERCHANT_ID = "merchant_id";
    public static final String CREDENTIALS_USERNAME = "username";
    public static final String CREDENTIALS_PASSWORD = "password";
    public static final String CREDENTIALS_SHA_PASSPHRASE = "sha_passphrase";

    private Long id;
    private String gatewayName;
    private Map<String, String> credentials;

    public GatewayAccount(Long id, String gatewayName, Map<String, String> credentials) {
        this.id = id;
        this.gatewayName = gatewayName;
        this.credentials = credentials;
    }

    @JsonProperty("gateway_account_id")
    public Long getId() {
        return id;
    }

    @JsonProperty("payment_provider")
    public String getGatewayName() {
        return gatewayName;
    }

    public Map<String, String> getCredentials() {
        return credentials;
    }

    public Map<String, String> withoutCredentials() {
        return ImmutableMap.of(
                "gateway_account_id", String.valueOf(id),
                "payment_provider", gatewayName);
    }

    public static GatewayAccount valueOf(GatewayAccountEntity entity) {
        return new GatewayAccount(entity.getId(), entity.getGatewayName(), entity.getCredentials());
    }
}
