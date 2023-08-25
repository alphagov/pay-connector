package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;

import java.util.Map;

public class StripeTokenRequest extends StripePostRequest {
    private final String paymentData;
    private String displayName;

    private String network;

    private String transactionIdentifier;


    private StripeTokenRequest(GatewayAccountEntity gatewayAccount, String idempotencyKey, StripeGatewayConfig stripeGatewayConfig, GatewayCredentials credentials, WalletAuthorisationData walletAuthorisationData) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.paymentData = walletAuthorisationData.getPaymentInfo().getRawPaymentData();
        this.displayName = walletAuthorisationData.getPaymentInfo().getDisplayName();
        this.network = walletAuthorisationData.getPaymentInfo().getNetwork();
        this.transactionIdentifier = walletAuthorisationData.getPaymentInfo().getTransactionIdentifier();
    }

    public static StripeTokenRequest of(WalletAuthorisationGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeTokenRequest(request.getGatewayAccount(), request.getGovUkPayPaymentId(), stripeGatewayConfig, request.getGatewayCredentials(), request.getWalletAuthorisationData());
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

    @Override
    protected String idempotencyKeyType() {
        return "token";
    }
}
