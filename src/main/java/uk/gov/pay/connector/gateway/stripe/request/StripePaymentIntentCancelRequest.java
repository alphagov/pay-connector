package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;

public class StripePaymentIntentCancelRequest extends StripePostRequest {
    private final String stripePaymentIntentId;

    private StripePaymentIntentCancelRequest(GatewayAccountEntity gatewayAccount, String stripePaymentIntentId,
                                             String idempotencyKey, StripeGatewayConfig stripeGatewayConfig,
                                             GatewayCredentials credentials) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.stripePaymentIntentId = stripePaymentIntentId;
    }

    public static StripePaymentIntentCancelRequest of(CancelGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripePaymentIntentCancelRequest(
                request.gatewayAccount(),
                request.transactionId(),
                request.externalChargeId(),
                stripeGatewayConfig,
                request.gatewayCredentials()
        );
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.CANCEL;
    }

    protected String urlPath() {
        return "/v1/payment_intents/" + stripePaymentIntentId + "/cancel" ;
    }
}
