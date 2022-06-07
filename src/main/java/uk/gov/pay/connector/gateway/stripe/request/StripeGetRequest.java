package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.request.GatewayClientGetRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;
import java.util.Map;

public abstract class StripeGetRequest implements GatewayClientGetRequest {
    
    private GatewayAccountEntity gatewayAccount;
    private StripeGatewayConfig stripeGatewayConfig;
    
    protected StripeGetRequest(GatewayAccountEntity gatewayAccount, StripeGatewayConfig stripeGatewayConfig) {
        this.gatewayAccount = gatewayAccount;
        this.stripeGatewayConfig = stripeGatewayConfig;
    }
    
    @Override
    public URI getUrl() {
        return URI.create(stripeGatewayConfig.getUrl() + urlPath());
    }

    @Override
    public Map<String, String> getHeaders() {
        return AuthUtil.getStripeAuthHeader(stripeGatewayConfig, gatewayAccount.isLive());
    }

    @Override
    public String getGatewayAccountType() {
        return gatewayAccount.getType();
    }

    @Override
    public PaymentGatewayName getPaymentProvider() {
        return PaymentGatewayName.STRIPE;
    }

    @Override
    public Map<String, String> getQueryParams() {
        return Map.of();
    }
    
    protected abstract String urlPath();
}
