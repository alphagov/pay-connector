package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.HashMap;
import java.util.Map;

public class StripePaymentIntentWithoutAuthoriseRequest extends StripeRequest {

    private final String amount;
    private final String transferGroup;
    private final String description;
    private boolean moto;


    private StripePaymentIntentWithoutAuthoriseRequest(
            GatewayAccountEntity gatewayAccount, String idempotencyKey, StripeGatewayConfig stripeGatewayConfig,
            String amount, String transferGroup,
            String description, boolean moto, Map<String, String> credentials) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.amount = amount;
        this.transferGroup = transferGroup;
        this.description = description;
        this.moto = moto;
    }

    public static StripePaymentIntentWithoutAuthoriseRequest of(
            ChargeEntity chargeEntity,
            StripeGatewayConfig stripeGatewayConfig) {
        return new StripePaymentIntentWithoutAuthoriseRequest(
                chargeEntity.getGatewayAccount(),
                chargeEntity.getExternalId(),
                stripeGatewayConfig,
                String.valueOf(chargeEntity.getAmount()),
                chargeEntity.getExternalId(),
                chargeEntity.getDescription(),
                chargeEntity.isMoto(),
                chargeEntity.getGatewayAccountCredentialsEntity().getCredentials()
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
                "amount", amount,
                "confirmation_method", "automatic",
                "capture_method", "manual",
                "currency", "GBP",
                "description", description,
                "transfer_group", transferGroup,
                "on_behalf_of", stripeConnectAccountId,
                "confirm", "false"));

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
