package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.HashMap;
import java.util.Map;

public class StripeTransferOutRequest extends StripeTransferRequest {

    private StripeTransferOutRequest(String amount,
                                     GatewayAccountEntity gatewayAccount,
                                     String sourceTransactionId,
                                     String idempotencyKey,
                                     StripeGatewayConfig stripeGatewayConfig
    ) {
        super(amount, gatewayAccount, sourceTransactionId, idempotencyKey, stripeGatewayConfig);
    }

    public static StripeTransferOutRequest of(String amount, CaptureGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeTransferOutRequest(
                amount,
                request.getGatewayAccount(),
                request.getTransactionId(),
                request.getExternalId(),
                stripeGatewayConfig
        );
    }

    @Override
    protected Map<String, String> params() {
        Map<String, String> params = new HashMap<>();
        Map<String, String> transferOutParams = Map.of(
                "destination", stripeConnectAccountId,
                "source_transaction", stripeChargeId
        );
        Map<String, String> commonParams = super.params();
        params.putAll(transferOutParams);
        params.putAll(commonParams);
        
        return params;
    }

    @Override
    protected String getIdempotencyKeyType() {
        return "transfer_out";
    }
    
    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.CAPTURE;
    }
}
