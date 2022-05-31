package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;

import static uk.gov.pay.connector.gateway.stripe.request.StripeTransferMetadata.GOVUK_PAY_TRANSACTION_EXTERNAL_ID;

public class StripeQueryPaymentStatusRequest extends StripeRequest{

    private String chargeExternalId;

    private StripeQueryPaymentStatusRequest(GatewayAccountEntity gatewayAccount, String idempotencyKey,
                                           StripeGatewayConfig stripeGatewayConfig, Map<String, String> credentials,
                                           String chargeExternalId) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.chargeExternalId = chargeExternalId;
    }

    public static StripeQueryPaymentStatusRequest of(GatewayAccountEntity gatewayAccount,
                                          StripeGatewayConfig stripeGatewayConfig, Map<String, String> credentials,
                                          String chargeExternalId) {
        return new StripeQueryPaymentStatusRequest(
                gatewayAccount,
                null,
                stripeGatewayConfig,
                credentials,
                chargeExternalId
        );
    }

    @Override
    protected String urlPath() {
        return "/v1/payment_intents/search";
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.QUERY;
    }

    @Override
    protected Map<String, String> params() {
        return Map.of("query", String.format("metadata['%s']:'%s'", GOVUK_PAY_TRANSACTION_EXTERNAL_ID, chargeExternalId));
    }

    public String getChargeExternalId() {
        return chargeExternalId;
    }
}
