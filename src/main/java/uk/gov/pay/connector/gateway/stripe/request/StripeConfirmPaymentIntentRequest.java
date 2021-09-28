package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.HashMap;
import java.util.Map;

public class StripeConfirmPaymentIntentRequest extends StripeRequest {

    private final String paymentMethodId;
    private final String paymentIntentId;
    private final String frontendUrl;
    private final String chargeExternalId;

    private StripeConfirmPaymentIntentRequest(
            GatewayAccountEntity gatewayAccount, String idempotencyKey, StripeGatewayConfig stripeGatewayConfig,
            String paymentMethodId, Map<String, String> credentials, String paymentIntentId, String frontendUrl, String chargeExternalId) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.paymentMethodId = paymentMethodId;
        this.paymentIntentId = paymentIntentId;
        this.frontendUrl = frontendUrl;
        this.chargeExternalId = chargeExternalId;
    }

    public static StripeConfirmPaymentIntentRequest of(
            CardAuthorisationGatewayRequest request,
            String paymentMethodId,
            StripeGatewayConfig stripeGatewayConfig,
            String frontendUrl) {
        return new StripeConfirmPaymentIntentRequest(
                request.getGatewayAccount(),
                request.getChargeExternalId(),
                stripeGatewayConfig,
                paymentMethodId,
                request.getGatewayCredentials(),
                request.getCharge().getGatewayTransactionId(),
                frontendUrl,
                request.getChargeExternalId()
        );
    }

    @Override
    protected String urlPath() {
        return String.format("/v1/payment_intents/%s/confirm", paymentIntentId);
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.AUTHORISE;
    }

    @Override
    protected Map<String, String> params() {
        return new HashMap<>(Map.of(
                "payment_method", paymentMethodId,
                "return_url", String.format("%s/card_details/%s/3ds_required_in", frontendUrl, chargeExternalId)));
    }

    @Override
    protected String idempotencyKeyType() {
        return "payment_intent_confirmation";
    }
}
