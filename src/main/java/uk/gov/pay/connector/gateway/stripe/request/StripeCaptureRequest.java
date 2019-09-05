package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Collections;
import java.util.List;

public abstract class StripeCaptureRequest extends StripeRequest {
    protected StripeCaptureRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig
    ) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
    }
    
    @Override
    protected List<String> expansionFields() {
        return Collections.singletonList("balance_transaction");
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.CAPTURE;
    }
}
