package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Collections;
import java.util.List;

public class StripeCaptureRequest extends StripeRequest {

    private String stripeChargeId;

    private StripeCaptureRequest(
            GatewayAccountEntity gatewayAccount,
            String stripeChargeId,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig
    ) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.stripeChargeId = stripeChargeId;
    }
    
    public static StripeCaptureRequest of(CaptureGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeCaptureRequest(
                request.getGatewayAccount(),
                request.getTransactionId(),
                request.getExternalId(),
                stripeGatewayConfig
        );
    }

    protected String urlPath() {
        return "/v1/charges/" + stripeChargeId + "/capture";
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
