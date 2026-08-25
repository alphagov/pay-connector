package uk.gov.pay.connector.gateway.adyen.utils;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.records.AdyenAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.records.ApplePayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.records.GooglePayAuthoriseRequest;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE_APPLE_PAY;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE_GOOGLE_PAY;

public class AdyenAuthoriseRequestToGatewayOrderConverter {

    private final JsonObjectMapper jsonObjectMapper;

    @Inject
    public AdyenAuthoriseRequestToGatewayOrderConverter(JsonObjectMapper jsonObjectMapper) {
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public GatewayOrder convert(AdyenAuthoriseRequest adyenAuthoriseRequest) {
        String json = jsonObjectMapper.objectToString(adyenAuthoriseRequest);
        
        OrderRequestType orderRequestType = switch (adyenAuthoriseRequest) {
            case ApplePayAuthoriseRequest _ -> AUTHORISE_APPLE_PAY;
            case GooglePayAuthoriseRequest _ -> AUTHORISE_GOOGLE_PAY;
            default -> AUTHORISE;
        };

        return new GatewayOrder(orderRequestType, json, MediaType.APPLICATION_JSON_TYPE);
    }

}
