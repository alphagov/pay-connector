package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.HashMap;
import java.util.Map;

import static java.util.Map.entry;

public class StripePaymentIntentRequest extends StripePostRequest {

    private static final String GOVUK_PAY_TRANSACTION_EXTERNAL_ID = "govuk_pay_transaction_external_id";

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
                request.getGovUkPayPaymentId(),
                stripeGatewayConfig,
                request.getAmount(),
                paymentMethodId,
                request.getGovUkPayPaymentId(),
                frontendUrl,
                request.getGovUkPayPaymentId(),
                request.getDescription(),
                request.isMoto(),
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
        Map<String, String> params = new HashMap<>(Map.ofEntries(
                entry("payment_method", paymentMethodId),
                entry("amount", amount),
                entry("confirmation_method", "automatic"),
                entry("capture_method", "manual"),
                entry("currency", "GBP"),
                entry("description", description),
                entry("transfer_group", transferGroup),
                entry("on_behalf_of", stripeConnectAccountId),
                entry("confirm", "true"),
                entry("return_url", String.format("%s/card_details/%s/3ds_required_in", frontendUrl, chargeExternalId)),
                entry(String.format("metadata[%s]", GOVUK_PAY_TRANSACTION_EXTERNAL_ID), chargeExternalId)));

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
