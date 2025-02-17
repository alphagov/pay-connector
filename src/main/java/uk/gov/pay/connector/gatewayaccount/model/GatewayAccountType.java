package uk.gov.pay.connector.gatewayaccount.model;

public enum GatewayAccountType {
    TEST("test"), LIVE("live");

    private final String value;

    GatewayAccountType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static GatewayAccountType fromString(String type) {
        for (GatewayAccountType typeEnum : GatewayAccountType.values()) {
            if (typeEnum.toString().equalsIgnoreCase(type)) {
                return typeEnum;
            }
        }
        throw new IllegalArgumentException("gateway account type has to be one of (test, live)");
    }
}
