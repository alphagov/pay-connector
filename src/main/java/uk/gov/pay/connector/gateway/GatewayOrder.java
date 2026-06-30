package uk.gov.pay.connector.gateway;

import jakarta.ws.rs.core.MediaType;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GatewayOrder that = (GatewayOrder) o;
        return orderRequestType == that.orderRequestType
                && Objects.equals(payload, that.payload)
                && Objects.equals(mediaType, that.mediaType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderRequestType, payload, mediaType);
    }

}
