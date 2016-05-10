package uk.gov.pay.connector.model.api;

public enum LegacyChargeStatus {
    LEGACY_EXT_CREATED("CREATED"),
    LEGACY_EXT_IN_PROGRESS("IN PROGRESS"),
    LEGACY_EXT_SUCCEEDED("SUCCEEDED"),
    LEGACY_EXT_FAILED("FAILED"),
    LEGACY_EXT_SYSTEM_CANCELLED("SYSTEM CANCELLED"),
    LEGACY_EXT_EXPIRED("EXPIRED"),
    LEGACY_EXT_USER_CANCELLED("USER CANCELLED");

    private final String value;

    LegacyChargeStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static LegacyChargeStatus fromString(String legacyStatus) {
        for (LegacyChargeStatus v : values()) {
            if (v.getValue().equals(legacyStatus)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Legacy charge status not recognized: " + legacyStatus);
    }
}
