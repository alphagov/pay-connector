package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;

public class StripeChargeCancelRequest extends StripeRequest {
    private final String stripeChargeId;
    
    private StripeChargeCancelRequest(GatewayAccountEntity gatewayAccount, String stripeChargeId, String idempotencyKey, StripeGatewayConfig stripeGatewayConfig) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.stripeChargeId = stripeChargeId;
    }

    public static StripeChargeCancelRequest of(CancelGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeChargeCancelRequest(
                request.getGatewayAccount(),
                request.getTransactionId(),
                request.getExternalChargeId(),
                stripeGatewayConfig
        );
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.CANCEL;
    }

    @Override
    protected Map<String, String> params() {
        return Map.of("charge", stripeChargeId);
    }

    protected String urlPath() {
        return "/v1/refunds";
    }
}
