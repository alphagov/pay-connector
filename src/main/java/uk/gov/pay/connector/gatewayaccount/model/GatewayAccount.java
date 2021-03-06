package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Objects;

public class GatewayAccount {
    public static final String CREDENTIALS_MERCHANT_ID = "merchant_id";
    public static final String CREDENTIALS_USERNAME = "username";
    public static final String CREDENTIALS_PASSWORD = "password";
    public static final String CREDENTIALS_SHA_IN_PASSPHRASE = "sha_in_passphrase";
    public static final String CREDENTIALS_SHA_OUT_PASSPHRASE = "sha_out_passphrase";
    public static final String FIELD_NOTIFY_API_TOKEN = "api_token";
    public static final String FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID = "template_id";
    public static final String FIELD_NOTIFY_REFUND_ISSUED_TEMPLATE_ID = "refund_issued_template_id";

    private Long id;
    private String gatewayName;
    private Map<String, String> credentials;
    private GatewayAccountType type;

    public GatewayAccount(Long id, String gatewayName, Map<String, String> credentials, GatewayAccountType type) {
        this.id = id;
        this.gatewayName = gatewayName;
        this.credentials = credentials;
        this.type = type;
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
        return new GatewayAccount(entity.getId(), entity.getGatewayName(), entity.getCredentials(), GatewayAccountType.fromString(entity.getType()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GatewayAccount that = (GatewayAccount) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(gatewayName, that.gatewayName) &&
                Objects.equals(credentials, that.credentials);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, gatewayName, credentials);
    }

    public GatewayAccountType getType() {
        return type;
    }
}
