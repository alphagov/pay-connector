package uk.gov.pay.connector.paymentprocessor.model;

public enum Exemption3ds {

    EXEMPTION_NOT_REQUESTED("not requested"),
    EXEMPTION_HONOURED("honoured"),
    EXEMPTION_REJECTED("rejected"),
    EXEMPTION_OUT_OF_SCOPE("out of scope");

    private String displayName;

    Exemption3ds(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
