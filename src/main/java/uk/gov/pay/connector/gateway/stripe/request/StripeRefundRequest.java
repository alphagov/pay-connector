package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;

public class StripeRefundRequest extends StripeRequest {
    private final String stripeChargeId;
    private final String amount;
    
    private StripeRefundRequest(
            String amount,
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            String stripeChargeId,
            StripeGatewayConfig stripeGatewayConfig
            ) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.stripeChargeId = stripeChargeId;
        this.amount = amount;
    }
    
    public static StripeRefundRequest of(RefundGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeRefundRequest(              
                request.getAmount(),
                request.getGatewayAccount(),
                request.getRefundExternalId(),
                request.getTransactionId(),
                stripeGatewayConfig
        );
    }

    protected String urlPath() {
        return "/v1/refunds";
    }

    @Override
    protected Map<String, String> params() {
        return Map.of("charge", stripeChargeId, "amount", amount);
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.REFUND;
    }
}
