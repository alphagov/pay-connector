package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class StripeCaptureRequest extends StripePostRequest {
    protected StripeCaptureRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            Map<String, String> credentials) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
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
