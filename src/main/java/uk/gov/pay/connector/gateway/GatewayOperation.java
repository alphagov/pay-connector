package uk.gov.pay.connector.gateway;

public enum GatewayOperation {
    AUTHORISE("auth"),
    CAPTURE("capture"),
    REFUND("refund"),
    QUERY("query"),
    CANCEL("cancel"),
    VALIDATE_CREDENTIALS("validate_credentials");

    private final String description;

    GatewayOperation(String auth) {
        this.description = auth;
    }

    public String getConfigKey() {
        return description;
    }
}
