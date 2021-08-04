package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.GatewayClientRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;

public class StripeGetPaymentIntentRequest extends StripeRequest {
    private final String paymentIntentId;

    private StripeGetPaymentIntentRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            Map<String, String> credentials,
            String paymentIntentId) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.paymentIntentId = paymentIntentId;
    }

    public static GatewayClientRequest of(RefundGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeGetPaymentIntentRequest(
                request.getGatewayAccount(),
                request.getRefundExternalId(),
                stripeGatewayConfig,
                request.getGatewayCredentials(),
                request.getTransactionId()
        );
    }

    @Override
    protected String urlPath() {
        return "/v1/payment_intents/" + paymentIntentId;
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.REFUND;
    }

    @Override
    protected String idempotencyKeyType() {
        return "get_payment_intent";
    }
}
