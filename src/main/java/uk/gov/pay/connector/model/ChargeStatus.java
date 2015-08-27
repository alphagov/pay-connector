package uk.gov.pay.connector.model;

public enum ChargeStatus {
    CREATED("CREATED"),
    AUTHORIZATION_SUBMITTED("AUTHORIZATION SUBMITTED"),
    AUTHORIZATION_SUCCESS("AUTHORIZATION SUCCESS");

    private String value;

    private ChargeStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
