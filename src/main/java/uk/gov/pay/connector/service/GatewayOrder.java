package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.OrderRequestType;

public class GatewayOrder {

    private OrderRequestType orderRequestType;
    private String payload;

    public GatewayOrder(OrderRequestType orderRequestType, String payload) {
        this.orderRequestType = orderRequestType;
        this.payload = payload;
    }

    public OrderRequestType getOrderRequestType() {
        return orderRequestType;
    }

    public String getPayload() {
        return payload;
    }
}
