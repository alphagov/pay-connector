package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Map;

public class StripeSearchTransfersRequest extends StripeGetRequest {
    private final String transferGroupId;
    
    public StripeSearchTransfersRequest(
            GatewayAccountEntity gatewayAccount,
            StripeGatewayConfig stripeGatewayConfig,
            String transferGroupId) {
        super(gatewayAccount, stripeGatewayConfig);
        this.transferGroupId = transferGroupId;
    }
    
    @Override
    public OrderRequestType getOrderRequestType() {
        return OrderRequestType.QUERY;
    }

    @Override
    protected String urlPath() {
        return "/v1/transfers";
    }

    @Override
    public Map<String, String> getQueryParams() {
        return Map.of("transfer_group", transferGroupId);
    }
}
