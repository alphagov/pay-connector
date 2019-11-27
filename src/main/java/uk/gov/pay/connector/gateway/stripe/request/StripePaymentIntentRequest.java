package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;

import static java.util.Map.entry;

public class StripePaymentIntentRequest extends StripeRequest {

    private final String amount;
    private final String paymentMethodId;
    private final String transferGroup;
    private final String frontendUrl;
    private final String chargeExternalId;
    private final String description;


    public StripePaymentIntentRequest(
            GatewayAccountEntity gatewayAccount, String idempotencyKey, StripeGatewayConfig stripeGatewayConfig, 
            String amount, String paymentMethodId, String transferGroup, String frontendUrl, String chargeExternalId,
            String description) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.amount = amount;
        this.paymentMethodId = paymentMethodId;
        this.transferGroup = transferGroup;
        this.frontendUrl = frontendUrl;
        this.chargeExternalId = chargeExternalId;
        this.description = description;
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
                request.getChargeExternalId(),
                request.getDescription()
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
        return Map.ofEntries(
                entry("payment_method", paymentMethodId),
                entry("payment_method_options[card[request_three_d_secure]]", "any"),
                entry("amount", amount),
                entry("confirmation_method", "automatic"),
                entry("capture_method", "manual"),
                entry("currency", "GBP"),
                entry("transfer_group", transferGroup),
                entry("description", description),
                entry("on_behalf_of", stripeConnectAccountId),
                entry("confirm", "true"),
                entry("return_url", String.format("%s/card_details/%s/3ds_required_in",
                        frontendUrl, chargeExternalId))
        );
    }

    @Override
    protected String idempotencyKeyType() {
        return "payment_intent";
    }
}
