package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.OrderRequestType;

import java.util.Optional;

public class GatewayOrder {

    private OrderRequestType orderRequestType;
    private String payload;
    private String providerSessionId;

    public GatewayOrder(OrderRequestType orderRequestType, String payload, String providerSessionId) {
        this.orderRequestType = orderRequestType;
        this.payload = payload;
        this.providerSessionId = providerSessionId;
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
}
