package uk.gov.pay.connector.service;

import javax.ws.rs.core.MediaType;
import uk.gov.pay.connector.model.OrderRequestType;

import java.util.Optional;

public class GatewayOrder {

    private OrderRequestType orderRequestType;
    private String payload;
    private String providerSessionId;
    private MediaType mediaType;

    public GatewayOrder(OrderRequestType orderRequestType, String payload, String providerSessionId,
        MediaType mediaType) {
        this.orderRequestType = orderRequestType;
        this.payload = payload;
        this.providerSessionId = providerSessionId;
        this.mediaType = mediaType;
    }

    public OrderRequestType getOrderRequestType() {
        return orderRequestType;
    }

    public String getPayload() {
        return payload;
    }

    public Optional<String> getProviderSessionId() {
        return Optional.ofNullable(providerSessionId);
    }

    MediaType getMediaType() {
        return mediaType;
    }
}
