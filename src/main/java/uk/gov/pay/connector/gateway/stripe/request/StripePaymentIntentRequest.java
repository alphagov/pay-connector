package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.HashMap;
import java.util.Map;

public class StripePaymentIntentRequest extends StripeRequest {

    private final String amount;
    private final String paymentMethodId;
    private final String transferGroup;
    private final String frontendUrl;
    private final String chargeExternalId;
    private final String description;
    private boolean moto;


    private StripePaymentIntentRequest(
            GatewayAccountEntity gatewayAccount, String idempotencyKey, StripeGatewayConfig stripeGatewayConfig,
            String amount, String paymentMethodId, String transferGroup, String frontendUrl, String chargeExternalId,
            String description, boolean moto, Map<String, String> credentials) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.amount = amount;
        this.paymentMethodId = paymentMethodId;
        this.transferGroup = transferGroup;
        this.frontendUrl = frontendUrl;
        this.chargeExternalId = chargeExternalId;
        this.description = description;
        this.moto = moto;
    }

    public static StripePaymentIntentRequest of(
            CardAuthorisationGatewayRequest request,
            String paymentMethodId,
            StripeGatewayConfig stripeGatewayConfig,
            String frontendUrl) {
        return new StripePaymentIntentRequest(
                request.getGatewayAccount(),
                request.getChargeExternalId(),
                stripeGatewayConfig,
                request.getAmount(),
                paymentMethodId,
                request.getChargeExternalId(),
                frontendUrl,
                request.getChargeExternalId(),
                request.getDescription(),
                request.getCharge().isMoto(),
                request.getGatewayCredentials()
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
        Map<String, String> params = new HashMap<>(Map.of(
                "payment_method", paymentMethodId,
                "amount", amount,
                "confirmation_method", "automatic",
                "capture_method", "manual",
                "currency", "GBP",
                "description", description,
                "transfer_group", transferGroup,
                "on_behalf_of", stripeConnectAccountId,
                "confirm", "true",
                "return_url", String.format("%s/card_details/%s/3ds_required_in", frontendUrl, chargeExternalId)));

        if (moto) {
            params.put("payment_method_options[card[moto]]", "true");
        }
        return params;
    }

    @Override
    protected String idempotencyKeyType() {
        return "payment_intent";
    }
}
