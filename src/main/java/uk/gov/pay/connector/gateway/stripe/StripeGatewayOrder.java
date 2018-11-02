package uk.gov.pay.connector.gateway.stripe;

import org.json.JSONObject;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;

import javax.ws.rs.core.MediaType;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;

class StripeGatewayOrder extends GatewayOrder {
    private StripeGatewayOrder(OrderRequestType orderRequestType, String payload, String providerSessionId, MediaType mediaType) {
        super(orderRequestType, payload, providerSessionId, mediaType);
    }

    static StripeGatewayOrder anAuthorisationOrder(AuthorisationGatewayRequest request, String sourceId) {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", request.getAmount());
        params.put("currency", "GBP");
        params.put("description", request.getDescription());
        params.put("source", sourceId);
        params.put("capture", false);
        String payload = new JSONObject(params).toString();
        return new StripeGatewayOrder(OrderRequestType.AUTHORISE, payload, null, MediaType.APPLICATION_JSON_TYPE);
    }

    public static GatewayOrder newSource(AuthorisationGatewayRequest request) {
        Map<String, Object> sourceParams = new HashMap<>();
        sourceParams.put("type", "card");
        sourceParams.put("amount", request.getAmount());
        sourceParams.put("currency", "GBP");
        Map<String, Object> ownerParams = new HashMap<>();
        ownerParams.put("name", request.getAuthCardDetails().getCardHolder());
        sourceParams.put("owner", ownerParams);
        String payload = new JSONObject(sourceParams).toString();
        return new StripeGatewayOrder(OrderRequestType.AUTHORISE, payload, null, MediaType.APPLICATION_JSON_TYPE);
    }
}
