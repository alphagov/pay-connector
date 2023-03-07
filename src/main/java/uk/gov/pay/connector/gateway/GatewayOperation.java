package uk.gov.pay.connector.gateway;

public enum GatewayOperation {
    AUTHORISE("auth"),
    CAPTURE("capture"),
    REFUND("refund"),
    QUERY("query"),
    CANCEL("cancel"),
    VALIDATE_CREDENTIALS("validate_credentials"),
    DELETE_STORED_PAYMENT_DETAILS("delete_stored_payment_details");

    private final String description;

    GatewayOperation(String auth) {
        this.description = auth;
    }

    public String getConfigKey() {
        return description;
    }
}
