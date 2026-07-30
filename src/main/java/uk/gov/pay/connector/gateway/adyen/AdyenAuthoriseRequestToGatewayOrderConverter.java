package uk.gov.pay.connector.gateway.adyen;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.records.AdyenAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.records.ApplePayAuthoriseRequest;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE_APPLE_PAY;

public class AdyenAuthoriseRequestToGatewayOrderConverter {

    private final JsonObjectMapper jsonObjectMapper;

    @Inject
    public AdyenAuthoriseRequestToGatewayOrderConverter(JsonObjectMapper jsonObjectMapper) {
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public GatewayOrder convert(AdyenAuthoriseRequest adyenAuthoriseRequest) {
        String json = jsonObjectMapper.objectToString(adyenAuthoriseRequest);
        OrderRequestType orderRequestType = adyenAuthoriseRequest instanceof ApplePayAuthoriseRequest
                ? AUTHORISE_APPLE_PAY : AUTHORISE;
        return new GatewayOrder(orderRequestType, json, MediaType.APPLICATION_JSON_TYPE);
    }

}
