package uk.gov.pay.connector.model.domain;

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
