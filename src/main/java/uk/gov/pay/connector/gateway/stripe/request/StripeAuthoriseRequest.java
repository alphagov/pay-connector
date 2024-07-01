package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;

import java.util.Map;

public class StripeAuthoriseRequest extends StripePostRequest {

    private final String amount;
    private final String description;
    private final String sourceId;
    private final String transferGroup;
    private OrderRequestType orderRequestType;

    private StripeAuthoriseRequest(
            String amount,
            String description,
            String sourceId,
            String transferGroup,
            OrderRequestType orderRequestType,
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            GatewayCredentials credentials) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.amount = amount;
        this.description = description;
        this.sourceId = sourceId;
        this.transferGroup = transferGroup;
        this.orderRequestType = orderRequestType;
    }

    public static StripeAuthoriseRequest of(String sourceId, CardAuthorisationGatewayRequest authorisationRequest, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeAuthoriseRequest(
                authorisationRequest.amount(),
                authorisationRequest.description(),
                sourceId,
                authorisationRequest.govUkPayPaymentId(),
                OrderRequestType.AUTHORISE,
                authorisationRequest.gatewayAccount(),
                authorisationRequest.govUkPayPaymentId(),
                stripeGatewayConfig,
                (StripeCredentials) authorisationRequest.gatewayCredentials()
        );
    }

    public static StripeAuthoriseRequest of(String sourceId, Auth3dsResponseGatewayRequest authorisationRequest, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeAuthoriseRequest(
                authorisationRequest.amount(),
                authorisationRequest.description(),
                sourceId,
                authorisationRequest.getChargeExternalId(),
                OrderRequestType.AUTHORISE_3DS,
                authorisationRequest.gatewayAccount(),
                authorisationRequest.getChargeExternalId(),
                stripeGatewayConfig,
                authorisationRequest.gatewayCredentials()
        );
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return this.orderRequestType;
    }

    @Override
    protected String urlPath() {
        return "/v1/charges";
    }

    @Override
    protected Map<String, String> params() {
        return Map.of(
                "amount", amount,
                "currency", "GBP",
                "description", description,
                "source", sourceId,
                "capture", "false",
                "transfer_group", transferGroup,
                "on_behalf_of", stripeConnectAccountId
        );
    }
}
