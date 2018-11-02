package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.OrderRequestType;

import javax.ws.rs.core.MediaType;
import java.util.Optional;

public class GatewayOrder {

    private OrderRequestType orderRequestType;
    private String payload;
    // Wordldpay specific property, not needed for everyone
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

    public MediaType getMediaType() {
        return mediaType;
    }
}
