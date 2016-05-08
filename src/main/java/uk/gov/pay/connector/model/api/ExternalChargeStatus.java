package uk.gov.pay.connector.model.api;

import uk.gov.pay.connector.model.domain.ChargeStatus;

import static org.apache.commons.lang3.ArrayUtils.contains;

public enum ExternalChargeStatus {
    EXT_CREATED("CREATED"),
    EXT_IN_PROGRESS("IN PROGRESS"),
    EXT_SUCCEEDED("SUCCEEDED"),
    EXT_FAILED("FAILED"),
    EXT_SYSTEM_CANCELLED("SYSTEM CANCELLED"),
    EXT_EXPIRED("EXPIRED"),
    EXT_USER_CANCELLED("USER CANCELLED");

    private final String value;

    ExternalChargeStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ExternalChargeStatus fromString(String externalStatus) {
        for (ExternalChargeStatus v : values()) {
            if (v.getValue().equals(externalStatus)) {
                return v;
            }
        }
        throw new IllegalArgumentException("External charge status not recognized: " + externalStatus);
    }
}
