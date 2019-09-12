package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;

public class StripePaymentIntentRequest extends StripeRequest {

    private final String amount;
    private final String paymentMethodId;
    private final String transferGroup;
    private String frontendUrl;
    private String chargeExternalId;


    public StripePaymentIntentRequest(
            GatewayAccountEntity gatewayAccount, String idempotencyKey, StripeGatewayConfig stripeGatewayConfig, 
            String amount, String paymentMethodId, String transferGroup, String frontendUrl, String chargeExternalId) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.amount = amount;
        this.paymentMethodId = paymentMethodId;
        this.transferGroup = transferGroup;
        this.frontendUrl = frontendUrl;
        this.chargeExternalId = chargeExternalId;
    }

    public static StripePaymentIntentRequest of(
            CardAuthorisationGatewayRequest request,
            String paymentMethodId,
            StripeGatewayConfig stripeGatewayConfig,
            String frontendUrl
    ) {
        return new StripePaymentIntentRequest(
                request.getGatewayAccount(),
                request.getChargeExternalId(),
                stripeGatewayConfig,
                request.getAmount(),
                paymentMethodId,
                request.getChargeExternalId(),
                frontendUrl,
                request.getChargeExternalId()
        );
    }
    
    @Override
    protected String urlPath() {
        return "/v1/payment_intents";
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.AUTHORISE;
    }

    @Override
    protected Map<String, String> params() {
        return Map.of(
                "payment_method", paymentMethodId,
                "amount", amount,
                "confirmation_method", "automatic",
                "capture_method", "manual",
                "currency", "GBP",
                "transfer_group", transferGroup,
                "on_behalf_of", stripeConnectAccountId,
                "confirm", "true",
                "return_url", String.format("%s/card_details/%s/3ds_required_in", frontendUrl, chargeExternalId)
        );
    }

    @Override
    protected String idempotencyKeyType() {
        return "payment_intent";
    }
}
