package uk.gov.pay.connector.gateway;

import jakarta.ws.rs.core.MediaType;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

public record GatewayOrder(OrderRequestType orderRequestType, String payload, MediaType mediaType) {

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[orderRequestType=" + orderRequestType + ", payload=redacted"
                + ", mediaType=" + mediaType + ']';
    }

}
