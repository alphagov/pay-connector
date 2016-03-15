package uk.gov.pay.connector.model.domain;

import org.apache.commons.lang3.StringUtils;

public enum ChargeStatus {
    CREATED("CREATED"),
    ENTERING_CARD_DETAILS("ENTERING CARD DETAILS"),
    AUTHORISATION_READY("AUTHORISATION READY"),

    /**
     * TODO: Remove deprecated AUTHORISATION_SUBMITTED state after refactoring PP-543
     *
     * PP-543 Introduce per-provider charge status DTO's and mappings
     * Upon refactoring charge status SENT_FOR_AUTHORISATION can be mapped to a per-provider DTO status specific
     * for Worldpay instead of generic common status AUTHORISATION_SUBMITTED.
     */
    AUTHORISATION_SUBMITTED("AUTHORISATION SUBMITTED"),

    AUTHORISATION_SUCCESS("AUTHORISATION SUCCESS"),
    AUTHORISATION_REJECTED("AUTHORISATION REJECTED"),
    READY_FOR_CAPTURE("READY_FOR_CAPTURE"),
    SYSTEM_ERROR("SYSTEM ERROR"),
    SYSTEM_CANCELLED("SYSTEM CANCELLED"),
    CAPTURED("CAPTURED"),
    CAPTURE_SUBMITTED("CAPTURE SUBMITTED"),
    CAPTURE_UNKNOWN("CAPTURE UNKNOWN"),
    EXPIRE_CANCEL_PENDING("EXPIRE CANCEL PENDING"),
    EXPIRE_CANCEL_FAILED("EXPIRE CANCEL FAILED"),
    EXPIRED("EXPIRED");

    private String value;

    ChargeStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ChargeStatus chargeStatusFrom(String status) {
        for (ChargeStatus stat : values()) {
            if (StringUtils.equals(stat.getValue(), status)) {
                return stat;
            }
        }
        throw new IllegalArgumentException("charge status not recognized: " + status);
    }

    public String toString() {
        return this.getValue();
    }
}
