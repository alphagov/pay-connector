package uk.gov.pay.connector.gateway.stripe;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import uk.gov.pay.connector.common.exception.CredentialsException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;

import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;

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
        Map<String, Object> destinationParams = new HashMap<>();
        String stripeAccountId = request.getGatewayAccount().getCredentials().get("stripe_account_id");
        
        if (StringUtils.isBlank(stripeAccountId))
            throw new CredentialsException(format("There is no stripe_account_id for gateway account with id %s", request.getGatewayAccount().getId()));
            
        destinationParams.put("account", stripeAccountId);
        params.put("destination", destinationParams);
        String payload = new JSONObject(params).toString();
        return new StripeGatewayOrder(OrderRequestType.AUTHORISE, payload, null, MediaType.APPLICATION_JSON_TYPE);
    }

    static GatewayOrder newSource(AuthorisationGatewayRequest request) {
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
