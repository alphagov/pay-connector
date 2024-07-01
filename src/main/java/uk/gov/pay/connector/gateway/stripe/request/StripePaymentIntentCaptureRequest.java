package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;

import java.util.Collections;
import java.util.List;

public class StripePaymentIntentCaptureRequest extends StripeCaptureRequest {
    private final String stripeIdentifier;

    private StripePaymentIntentCaptureRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            String stripeIdentifier,
            GatewayCredentials credentials) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.stripeIdentifier = stripeIdentifier;
    }

    public static StripeCaptureRequest of(CaptureGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripePaymentIntentCaptureRequest(
                request.gatewayAccount(),
                request.externalId(),
                stripeGatewayConfig,
                request.transactionId(),
                request.gatewayCredentials()
        );
    }

    @Override
    protected List<String> expansionFields() {
        return Collections.singletonList("charges.data.balance_transaction");
    }

    @Override
    protected String urlPath() {
        return "/v1/payment_intents/" + stripeIdentifier + "/capture";
    }
}
