package uk.gov.pay.connector.paymentprocessor.model;

public enum OperationType {
    CAPTURE("Capture"),
    AUTHORISATION("Authorisation"),
    AUTHORISATION_3DS("3D Secure Response Authorisation"),
    CANCELLATION("Cancellation");

    private String value;

    OperationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
