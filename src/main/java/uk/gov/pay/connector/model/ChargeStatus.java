package uk.gov.pay.connector.model;

import org.apache.commons.lang3.StringUtils;

public enum ChargeStatus {
    CREATED("CREATED"),
    AUTHORIZATION_SUBMITTED("AUTHORIZATION SUBMITTED"),
    AUTHORIZATION_SUCCESS("AUTHORIZATION SUCCESS"),
    AUTHORIZATION_REJECTED("AUTHORIZATION REJECTED"),
    SYSTEM_ERROR("SYSTEM ERROR"),
    CAPTURED("CAPTURED");

    public static final String STATUS_KEY = "status";

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
}
