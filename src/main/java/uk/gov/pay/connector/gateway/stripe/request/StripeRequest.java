package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.request.GatewayClientRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class StripeRequest implements GatewayClientRequest {

    protected GatewayAccountEntity gatewayAccount;
    private String idempotencyKey;
    protected StripeGatewayConfig stripeGatewayConfig;
    protected String stripeConnectAccountId;


    protected StripeRequest(GatewayAccountEntity gatewayAccount, String idempotencyKey, StripeGatewayConfig stripeGatewayConfig) {
        this.gatewayAccount = gatewayAccount;
        this.idempotencyKey = idempotencyKey;
        this.stripeGatewayConfig = stripeGatewayConfig;
        String stripeConnectAccountId = gatewayAccount.getCredentials().get("stripe_account_id");
        if (gatewayAccount.getCredentials().get("stripe_account_id") == null) {
            throw new IllegalArgumentException("Cannot create StripeRequest with a gateway account with out a stripe account id set");
        }
        this.stripeConnectAccountId = stripeConnectAccountId;
    }

    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
    }

    public Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        Optional.ofNullable(idempotencyKey).ifPresent(idempotencyKey -> headers.put("Idempotency-Key", idempotencyKey));
        headers.putAll(AuthUtil.getStripeAuthHeader(stripeGatewayConfig, gatewayAccount.isLive()));

        return headers;
    }
}
