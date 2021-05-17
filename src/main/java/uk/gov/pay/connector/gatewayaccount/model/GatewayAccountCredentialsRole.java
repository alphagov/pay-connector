package uk.gov.pay.connector.gatewayaccount.model;

public enum GatewayAccountCredentialsRole {
    PRIMARY("PRIMARY"), SECONDARY("SECONDARY");

    private final String value;

    GatewayAccountCredentialsRole(String value) {
        this.value = value;
    }

    public String toString() {
        return value;
    }

    public static GatewayAccountCredentialsRole fromString(String role) {
        for (GatewayAccountCredentialsRole typeEnum : GatewayAccountCredentialsRole.values()) {
            if (typeEnum.toString().equalsIgnoreCase(role)) {
                return typeEnum;
            }
        }
        throw new IllegalArgumentException("gateway account credentials role has to be one of (PRIMARY, SECONDARY)");
    }
}
