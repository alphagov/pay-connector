package uk.gov.pay.connector.gateway;

public enum GatewayOperation {
    AUTHORISE("auth"),
    CAPTURE("capture"),
    REFUND("refund"),
    QUERY("query"),
    CANCEL("cancel"), 
    RECOUP_FEE("recoup_fee");

    private final String description;

    GatewayOperation(String auth) {
        this.description = auth;
    }

    public String getConfigKey() {
        return description;
    }
}
