package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.request.GatewayClientRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class StripeRequest implements GatewayClientRequest {

    protected GatewayAccountEntity gatewayAccount;
    private String idempotencyKey;
    protected StripeGatewayConfig stripeGatewayConfig;
    protected String stripeConnectAccountId;

    protected StripeRequest(GatewayAccountEntity gatewayAccount, String idempotencyKey, StripeGatewayConfig stripeGatewayConfig) {
        if (gatewayAccount == null) {
            throw new IllegalArgumentException("Cannot create StripeRequest without a gateway account");
        }

        String stripeAccountId = gatewayAccount.getCredentials().get("stripe_account_id");
        if (stripeAccountId == null) {
            throw new IllegalArgumentException("Cannot create StripeRequest with a gateway account with out a stripe account id set");
        }
        
        this.gatewayAccount = gatewayAccount;
        this.idempotencyKey = idempotencyKey;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.stripeConnectAccountId = stripeAccountId;
    }

    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
    }

    public Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        Optional.ofNullable(idempotencyKey).ifPresent(idempotencyKey -> headers.put("Idempotency-Key", getIdempotencyKeyType() + idempotencyKey));
        headers.putAll(AuthUtil.getStripeAuthHeader(stripeGatewayConfig, gatewayAccount.isLive()));

        return headers;
    }
    
    protected abstract  String getIdempotencyKeyType();
}
