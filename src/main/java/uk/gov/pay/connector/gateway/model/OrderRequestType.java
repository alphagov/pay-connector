package uk.gov.pay.connector.gateway.model;

public enum OrderRequestType {
    AUTHORISE("authorise"),
    AUTHORISE_3DS("authorise3DS"),
    AUTHORISE_APPLE_PAY("authoriseApplePay"),
    CAPTURE("capture"),
    CANCEL("cancel"),
    REFUND("refund");

    private final String name;

    OrderRequestType(String s) {
        name = s;
    }

    public String toString() {
        return this.name;
    }
}
