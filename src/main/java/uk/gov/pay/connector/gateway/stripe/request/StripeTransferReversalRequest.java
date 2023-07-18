package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;

import java.util.Map;

public class StripeTransferReversalRequest extends StripePostRequest {
    private String transferId;

    private StripeTransferReversalRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            String transferId,
            GatewayCredentials credentials) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.transferId = transferId;
    }

    public static StripeTransferReversalRequest of(String transferId, RefundGatewayRequest request,
                                                   StripeGatewayConfig stripeGatewayConfig) {
        return new StripeTransferReversalRequest(
                request.getGatewayAccount(),
                request.getRefundExternalId(),
                stripeGatewayConfig,
                transferId,
                request.getGatewayCredentials()
        );
    }

    public String urlPath() {
        return "/v1/transfers/" + transferId + "/reversals" ;
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.REFUND;
    }

    @Override
    protected String idempotencyKeyType() {
        return "reverse_transfer";
    }

    @Override
    public Map<String, String> headers() {
        return Map.of("Stripe-Account", stripeConnectAccountId);
    }
}
