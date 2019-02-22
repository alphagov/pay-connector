package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.OrderRequestType;

import javax.ws.rs.core.MediaType;
import java.util.Optional;

public class GatewayOrder {

    private OrderRequestType orderRequestType;
    private String payload;
    private MediaType mediaType;

    public GatewayOrder(OrderRequestType orderRequestType, String payload, MediaType mediaType) {
        this.orderRequestType = orderRequestType;
        this.payload = payload;
        this.mediaType = mediaType;
    }

    public OrderRequestType getOrderRequestType() {
        return orderRequestType;
    }

    public String getPayload() {
        return payload;
    }

    public MediaType getMediaType() {
        return mediaType;
    }
}
