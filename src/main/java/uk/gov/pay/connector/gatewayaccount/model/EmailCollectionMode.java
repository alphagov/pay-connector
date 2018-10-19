package uk.gov.pay.connector.gatewayaccount.model;

public enum EmailCollectionMode {
    MANDATORY, OPTIONAL, OFF;

    public static EmailCollectionMode fromString(String mode) {
        try {
            return EmailCollectionMode.valueOf(mode.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("EmailCollectionMode not recognized: " + mode);
        }
    }
}
