package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.HashMap;
import java.util.Map;

public class StripeCustomerRequest extends StripePostRequest {
    
    String name;
    String agreementId;

    private StripeCustomerRequest(
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            Map<String, String> credentials,
            String name,
            String agreementId
    ) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.name = name;
        this.agreementId = agreementId;
    }

    public static StripeCustomerRequest of(
            CardAuthorisationGatewayRequest request,
            StripeGatewayConfig stripeGatewayConfig) {
        var name = request.getAuthCardDetails().getCardHolder();
        return new StripeCustomerRequest(
                request.getGatewayAccount(),
                request.getGovUkPayPaymentId().concat(name),
                stripeGatewayConfig,
                request.getGatewayCredentials(),
                name,
                request.getAgreementId()
        );
    }

    @Override
    protected String urlPath() {
        return "/v1/customers";
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.STRIPE_CREATE_CUSTOMER;
    }

    @Override
    protected Map<String, String> params() {
        Map<String, String> params = new HashMap<>(Map.of(
                "name", name,
                "description", "Customer for agreement ".concat(agreementId)
        ));
        return params;
    }

    @Override
    protected String idempotencyKeyType() {
        return "customer";
    }
}
