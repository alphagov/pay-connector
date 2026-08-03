package uk.gov.pay.connector.gateway.adyen.utils;

import jakarta.ws.rs.core.MediaType;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.adyen.request.json.AuthoriseRequestPayload;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE;

public class AuthoriseRequestPayloadToGatewayOrderConverter {

    private final JsonObjectMapper jsonObjectMapper;
    
    public AuthoriseRequestPayloadToGatewayOrderConverter(JsonObjectMapper jsonObjectMapper) {
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public GatewayOrder convert(AuthoriseRequestPayload authoriseRequestPayload) {
        String json = jsonObjectMapper.objectToString(authoriseRequestPayload);
        return new GatewayOrder(AUTHORISE, json, MediaType.APPLICATION_JSON_TYPE);
    }

}
