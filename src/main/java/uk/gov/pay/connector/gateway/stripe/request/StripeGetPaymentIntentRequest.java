package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

public class StripeGetPaymentIntentRequest extends StripeGetRequest {
    private final String paymentIntentId;

    private StripeGetPaymentIntentRequest(
            GatewayAccountEntity gatewayAccount,
            StripeGatewayConfig stripeGatewayConfig,
            String paymentIntentId) {
        super(gatewayAccount, stripeGatewayConfig);
        this.paymentIntentId = paymentIntentId;
    }

    public static StripeGetPaymentIntentRequest of(RefundGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeGetPaymentIntentRequest(
                request.getGatewayAccount(),
                stripeGatewayConfig,
                request.getTransactionId()
        );
    }
    
    public static StripeGetPaymentIntentRequest of(ChargeEntity charge,
                                          StripeGatewayConfig stripeGatewayConfig) {
        return new StripeGetPaymentIntentRequest(
                charge.getGatewayAccount(),
                stripeGatewayConfig,
                charge.getGatewayTransactionId()
        );
    }

    @Override
    protected String urlPath() {
        return "/v1/payment_intents/" + paymentIntentId;
    }

    @Override
    public OrderRequestType getOrderRequestType() {
        return OrderRequestType.QUERY;
    }
}
