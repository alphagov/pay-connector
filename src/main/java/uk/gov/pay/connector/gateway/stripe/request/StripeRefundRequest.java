package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;

import java.util.Map;

public class StripeRefundRequest extends StripePostRequest {
    private final String stripeChargeId;
    private final String amount;
    
    private StripeRefundRequest(
            String amount,
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            String stripeChargeId,
            StripeGatewayConfig stripeGatewayConfig,
            GatewayCredentials credentials) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.stripeChargeId = stripeChargeId;
        this.amount = amount;
    }
    
    public static StripeRefundRequest of(RefundGatewayRequest request, String stripeChargeId,
                                         StripeGatewayConfig stripeGatewayConfig) {
        return new StripeRefundRequest(              
                request.amount(),
                request.gatewayAccount(),
                request.refundExternalId(),
                stripeChargeId,
                stripeGatewayConfig,
                request.getGatewayCredentials()
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
