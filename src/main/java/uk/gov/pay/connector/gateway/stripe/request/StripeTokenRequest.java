package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;

import java.util.Map;

public class StripeTokenRequest extends StripePostRequest {

    private final String paymentData;
    private String displayName;
    private String network;
    private String transactionIdentifier;

    private StripeTokenRequest(GatewayAccountEntity gatewayAccount, StripeGatewayConfig stripeGatewayConfig, GatewayCredentials credentials, ApplePayAuthRequest applePayAuthRequest) {
        super(gatewayAccount, null, stripeGatewayConfig, credentials);
        this.paymentData = applePayAuthRequest.getPaymentData();
        this.displayName = applePayAuthRequest.getPaymentInfo().getDisplayName();
        this.network = applePayAuthRequest.getPaymentInfo().getNetwork();
        this.transactionIdentifier = applePayAuthRequest.getPaymentInfo().getTransactionIdentifier();
    }

    public static StripeTokenRequest of(ApplePayAuthorisationGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeTokenRequest(request.gatewayAccount(), stripeGatewayConfig, request.gatewayCredentials(), request.applePayAuthRequest());
    }

    @Override
    protected Map<String, String> params() {
        return Map.of("pk_token", paymentData,
                "pk_token_instrument_name", displayName,
                "pk_token_payment_network", network,
                "pk_token_transaction_id", transactionIdentifier);
    }

    @Override
    protected String urlPath() {
        return "/v1/tokens";
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.AUTHORISE;
    }
}
