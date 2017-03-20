package uk.gov.pay.connector.service;

public enum SupportedPaymentGateway {
    WORLDPAY("WORLD_PAY"),
    SMARTPAY("SMART_PAY");

    private final String gatewayName;

    SupportedPaymentGateway(String value) {
        this.gatewayName = value;
    }

    public String getGatewayName() {
        return gatewayName;
    }

    public static class Unsupported extends RuntimeException {}
}
