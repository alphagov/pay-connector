package uk.gov.pay.connector.service;

public class GatewayOrder {

    private String type;
    private String payload;

    public GatewayOrder(String type, String payload) {
        this.type = type;
        this.payload = payload;
    }
    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }
}
