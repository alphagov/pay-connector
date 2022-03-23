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
    private final String customerId;
    private boolean moto;
    private boolean setupPaymentInstrument;


    private StripePaymentIntentRequest(
            GatewayAccountEntity gatewayAccount, String idempotencyKey, StripeGatewayConfig stripeGatewayConfig,
            String amount, String paymentMethodId, String transferGroup, String frontendUrl, String chargeExternalId,
            String description, boolean moto, boolean setupPaymentInstrument, Map<String, String> credentials, String customerId) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.amount = amount;
        this.paymentMethodId = paymentMethodId;
        this.transferGroup = transferGroup;
        this.frontendUrl = frontendUrl;
        this.chargeExternalId = chargeExternalId;
        this.description = description;
        this.moto = moto;
        this.setupPaymentInstrument = setupPaymentInstrument;
        this.customerId = customerId;
    }

    public static StripePaymentIntentRequest of(
            CardAuthorisationGatewayRequest request,
            String paymentMethodId,
            String customerId,
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
                request.getCharge().isSavePaymentInstrumentToAgreement(),
                request.getGatewayCredentials(),
                customerId
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
        if (setupPaymentInstrument) {
            params.put("setup_future_usage", "off_session");
            params.put("customer", customerId);
        }

        // if (authModeApi) {
            // params.put("off_session": true)
            // params.put("customer", customerId)
        // }
        return params;
    }

    @Override
    protected String idempotencyKeyType() {
        return "payment_intent";
    }
}
