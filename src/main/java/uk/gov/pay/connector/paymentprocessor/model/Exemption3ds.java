package uk.gov.pay.connector.paymentprocessor.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Exemption3ds {

    EXEMPTION_NOT_REQUESTED("not requested"),
    EXEMPTION_HONOURED("honoured"),
    EXEMPTION_REJECTED("rejected"),
    EXEMPTION_OUT_OF_SCOPE("out of scope");

    private String displayName;

    Exemption3ds(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}
