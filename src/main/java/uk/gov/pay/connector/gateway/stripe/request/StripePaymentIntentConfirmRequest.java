package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayClientRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;

public class StripePaymentIntentConfirmRequest extends StripeRequest {
    private final String paymentIntentId;
    private final String chargeExternalId;
    private final String frontendUrl;

    public StripePaymentIntentConfirmRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            String paymentIntentId,
            String frontendUrl,
            String chargeExternalId
    ) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.paymentIntentId = paymentIntentId;
        this.frontendUrl = frontendUrl;
        this.chargeExternalId = chargeExternalId;
    }

    public static GatewayClientRequest of(
            CardAuthorisationGatewayRequest request, String paymentIntentId, String frontendUrl, StripeGatewayConfig stripeGatewayConfig
    ) {
        return new StripePaymentIntentConfirmRequest(
                request.getGatewayAccount(),
                request.getChargeExternalId(),
                stripeGatewayConfig,
                paymentIntentId,
                frontendUrl,
                request.getChargeExternalId()
        );
    }

    @Override
    protected String urlPath() {
        return "/v1/payment_intents/" + paymentIntentId;
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.AUTHORISE;
    }

    @Override
    protected Map<String, String> params() {
        return Map.of(
                "return_url", String.format("%s/card_details/%s/3ds_required_in", frontendUrl, chargeExternalId)
        );
    }

    @Override
    protected String idempotencyKeyType() {
        return "confirm_payment_intent";
    }
}
