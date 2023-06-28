package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class GatewayAccount {
    public static final String CREDENTIALS_MERCHANT_ID = "merchant_id";
    public static final String CREDENTIALS_USERNAME = "username";
    public static final String CREDENTIALS_PASSWORD = "password";
    public static final String CREDENTIALS_SHA_IN_PASSPHRASE = "sha_in_passphrase";
    public static final String ONE_OFF_CUSTOMER_INITIATED = "one_off_customer_initiated";

    public static final String RECURRING_CUSTOMER_INITIATED = "recurring_customer_initiated";
    public static final String RECURRING_MERCHANT_INITIATED = "recurring_merchant_initiated";
    public static final String CREDENTIALS_SHA_OUT_PASSPHRASE = "sha_out_passphrase";
    public static final String CREDENTIALS_STRIPE_ACCOUNT_ID = "stripe_account_id";
    public static final String FIELD_NOTIFY_API_TOKEN = "api_token";
    public static final String FIELD_NOTIFY_PAYMENT_CONFIRMED_TEMPLATE_ID = "template_id";
    public static final String FIELD_NOTIFY_REFUND_ISSUED_TEMPLATE_ID = "refund_issued_template_id";

    private Long id;
    private String gatewayName;
    private GatewayAccountType type;

    public GatewayAccount(Long id, String gatewayName, GatewayAccountType type) {
        this.id = id;
        this.gatewayName = gatewayName;
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

    public static GatewayAccount valueOf(GatewayAccountEntity entity) {
        return new GatewayAccount(entity.getId(), entity.getGatewayName(), GatewayAccountType.fromString(entity.getType()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GatewayAccount that = (GatewayAccount) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(gatewayName, that.gatewayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, gatewayName);
    }

    public GatewayAccountType getType() {
        return type;
    }
}
