package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Collections;
import java.util.List;

public class StripeChargeCaptureRequest extends StripeCaptureRequest {
    private final String stripeIdentifier;
    
    private StripeChargeCaptureRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            String stripeIdentifier
    ) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.stripeIdentifier = stripeIdentifier;
    }
    
    public static StripeCaptureRequest of(CaptureGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        
        
        return new StripeChargeCaptureRequest(
                request.getGatewayAccount(),
                request.getExternalId(),
                stripeGatewayConfig,
                request.getTransactionId()
        );
    }

    @Override
    protected String urlPath() {
        return "/v1/charges/" + stripeIdentifier + "/capture";
    }

    @Override
    protected List<String> expansionFields() {
        return Collections.singletonList("balance_transaction");
    }
}
