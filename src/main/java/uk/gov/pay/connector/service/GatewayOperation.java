package uk.gov.pay.connector.service;

public enum GatewayOperation {
    AUTHORISE("auth"),
    CAPTURE("cancel"),
    REFUND("refund"),
    CANCEL("capture");


    private final String description;

    GatewayOperation(String auth) {
        this.description = auth;
    }

    public String getConfigKey() {
        return description;
    }
}
