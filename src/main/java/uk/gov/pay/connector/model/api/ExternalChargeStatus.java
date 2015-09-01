package uk.gov.pay.connector.model.api;

import uk.gov.pay.connector.model.ChargeStatus;

import static org.apache.commons.lang3.ArrayUtils.contains;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUBMITTED;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUCCESS;
import static uk.gov.pay.connector.model.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.ChargeStatus.chargeStatusFrom;

public enum ExternalChargeStatus {
    EXT_CREATED("CREATED", CREATED),
    EXT_IN_PROGRESS("IN PROGRESS", AUTHORIZATION_SUBMITTED, AUTHORIZATION_SUCCESS);

    private final String value;
    private final ChargeStatus[] innerStates;

    ExternalChargeStatus(String value, ChargeStatus... innerStates) {
        this.value = value;
        this.innerStates = innerStates;
    }

    public String getValue() {
        return value;
    }

    public static ExternalChargeStatus mapFromStatus(ChargeStatus status) {
        for (ExternalChargeStatus ext : values()) {
            if (contains(ext.innerStates, status)) {
                return ext;
            }
        }
        throw new IllegalArgumentException("charge status not recognized: " + status);
    }

    public static ExternalChargeStatus mapFromStatus(String status) {
        return mapFromStatus(chargeStatusFrom(status));
    }
}
