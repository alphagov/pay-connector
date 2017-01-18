package uk.gov.pay.connector.model;

public enum OrderRequestType {
    AUTHORISE("authorise"),
    CAPTURE("capture"),
    CANCEL("cancel"),
    REFUND("refund");

    private final String name;

    private OrderRequestType(String s) {
        name = s;
    }

    public String toString() {
        return this.name;
    }
}
