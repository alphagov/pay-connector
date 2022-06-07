package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;

import static uk.gov.pay.connector.gateway.stripe.request.StripeTransferMetadata.GOVUK_PAY_TRANSACTION_EXTERNAL_ID;

public class StripeQueryPaymentStatusRequest extends StripeGetRequest {

    private String chargeExternalId;

    private StripeQueryPaymentStatusRequest(GatewayAccountEntity gatewayAccount,
                                            StripeGatewayConfig stripeGatewayConfig,
                                            String chargeExternalId) {
        super(gatewayAccount, stripeGatewayConfig);
        this.chargeExternalId = chargeExternalId;
    }

    public static StripeQueryPaymentStatusRequest of(GatewayAccountEntity gatewayAccount,
                                                     StripeGatewayConfig stripeGatewayConfig,
                                                     String chargeExternalId) {
        return new StripeQueryPaymentStatusRequest(
                gatewayAccount,
                stripeGatewayConfig,
                chargeExternalId
        );
    }

    @Override
    protected String urlPath() {
        return "/v1/payment_intents/search";
    }

    @Override
    public OrderRequestType getOrderRequestType() {
        return OrderRequestType.QUERY;
    }

    @Override
    public Map<String, String> getQueryParams() {
        return Map.of("query", String.format("metadata['%s']:'%s'", GOVUK_PAY_TRANSACTION_EXTERNAL_ID, chargeExternalId));
    }

    public String getChargeExternalId() {
        return chargeExternalId;
    }
}
