package uk.gov.pay.connector.model.domain;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.*;

public enum ChargeStatus {
    CREATED("CREATED", EXT_CREATED),
    ENTERING_CARD_DETAILS("ENTERING CARD DETAILS", EXT_IN_PROGRESS),
    AUTHORISATION_READY("AUTHORISATION READY", EXT_IN_PROGRESS),

    /**
     * TODO: Remove deprecated AUTHORISATION_SUBMITTED state after refactoring PP-543
     *
     * PP-543 Introduce per-provider charge status DTO's and mappings
     * Upon refactoring charge status SENT_FOR_AUTHORISATION can be mapped to a per-provider DTO status specific
     * for Worldpay instead of generic common status AUTHORISATION_SUBMITTED.
     */
    AUTHORISATION_SUBMITTED("AUTHORISATION SUBMITTED", EXT_IN_PROGRESS),

    AUTHORISATION_SUCCESS("AUTHORISATION SUCCESS", EXT_IN_PROGRESS),
    AUTHORISATION_REJECTED("AUTHORISATION REJECTED", EXT_FAILED),
    AUTHORISATION_ERROR("AUTHORISATION ERROR", EXT_FAILED),

    CAPTURE_READY("CAPTURE READY", EXT_IN_PROGRESS),
    CAPTURED("CAPTURED", EXT_SUCCEEDED),
    CAPTURE_SUBMITTED("CAPTURE SUBMITTED", EXT_SUCCEEDED),
    CAPTURE_ERROR("CAPTURE ERROR", EXT_FAILED),

    EXPIRE_CANCEL_PENDING("EXPIRE CANCEL PENDING", EXT_EXPIRED),
    EXPIRE_CANCEL_FAILED("EXPIRE CANCEL FAILED", EXT_EXPIRED),
    EXPIRED("EXPIRED", EXT_EXPIRED),

    CANCEL_READY("CANCEL READY", EXT_IN_PROGRESS),
    CANCEL_ERROR("CANCEL ERROR", EXT_FAILED),

    SYSTEM_CANCELLED("SYSTEM CANCELLED", EXT_SYSTEM_CANCELLED),

    USER_CANCELLED("USER CANCELLED", EXT_USER_CANCELLED),
    USER_CANCEL_ERROR("USER CANCEL ERROR", EXT_USER_CANCELLED);

    private String value;
    private ExternalChargeStatus externalStatus;

    ChargeStatus(String value, ExternalChargeStatus externalStatus) {
        this.value = value;
        this.externalStatus = externalStatus;
    }

    public String getValue() {
        return value;
    }

    public String toString() {
        return this.getValue();
    }

    public ExternalChargeStatus toExternal() {
        return externalStatus;
    }


    public static ChargeStatus fromString(String status) {
        for (ChargeStatus stat : values()) {
            if (StringUtils.equals(stat.getValue(), status)) {
                return stat;
            }
        }
        throw new IllegalArgumentException("charge status not recognized: " + status);
    }

    public static List<ChargeStatus> fromExternal(ExternalChargeStatus externalStatus) {
        return stream(values()).filter(status -> status.toExternal().equals(externalStatus)).collect(Collectors.toList());
    }
}
