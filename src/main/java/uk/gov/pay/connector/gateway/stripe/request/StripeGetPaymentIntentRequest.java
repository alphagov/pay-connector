package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;

public class StripeGetPaymentIntentRequest extends StripeGetRequest {
    private final String paymentIntentId;

    public StripeGetPaymentIntentRequest(
            GatewayAccountEntity gatewayAccount,
            StripeGatewayConfig stripeGatewayConfig,
            String paymentIntentId) {
        super(gatewayAccount, stripeGatewayConfig);
        this.paymentIntentId = paymentIntentId;
    }

    public static StripeGetPaymentIntentRequest of(RefundGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeGetPaymentIntentRequest(
                request.gatewayAccount(),
                stripeGatewayConfig,
                request.transactionId()
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

    @Override
    public Map<String, String> getQueryParams() {
        return Map.of("expand[]", "charges.data.balance_transaction");
    }
}
