package uk.gov.pay.connector.model.domain;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.api.LegacyChargeStatus;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static uk.gov.pay.connector.model.api.LegacyChargeStatus.*;

public enum ChargeStatus {
    CREATED("CREATED", LEGACY_EXT_CREATED),
    ENTERING_CARD_DETAILS("ENTERING CARD DETAILS", LEGACY_EXT_IN_PROGRESS),
    AUTHORISATION_READY("AUTHORISATION READY", LEGACY_EXT_IN_PROGRESS),

    /**
     * TODO: Remove deprecated AUTHORISATION_SUBMITTED state after refactoring PP-543
     *
     * PP-543 Introduce per-provider charge status DTO's and mappings
     * Upon refactoring charge status SENT_FOR_AUTHORISATION can be mapped to a per-provider DTO status specific
     * for Worldpay instead of generic common status AUTHORISATION_SUBMITTED.
     */
    AUTHORISATION_SUBMITTED("AUTHORISATION SUBMITTED", LEGACY_EXT_IN_PROGRESS),

    AUTHORISATION_SUCCESS("AUTHORISATION SUCCESS", LEGACY_EXT_IN_PROGRESS),
    AUTHORISATION_REJECTED("AUTHORISATION REJECTED", LEGACY_EXT_FAILED),
    AUTHORISATION_ERROR("AUTHORISATION ERROR", LEGACY_EXT_FAILED),

    CAPTURE_READY("CAPTURE READY", LEGACY_EXT_IN_PROGRESS),
    CAPTURED("CAPTURED", LEGACY_EXT_SUCCEEDED),
    CAPTURE_SUBMITTED("CAPTURE SUBMITTED", LEGACY_EXT_SUCCEEDED),
    CAPTURE_ERROR("CAPTURE ERROR", LEGACY_EXT_FAILED),

    EXPIRE_CANCEL_PENDING("EXPIRE CANCEL PENDING", LEGACY_EXT_EXPIRED),
    EXPIRE_CANCEL_FAILED("EXPIRE CANCEL FAILED", LEGACY_EXT_EXPIRED),
    EXPIRED("EXPIRED", LEGACY_EXT_EXPIRED),

    CANCEL_READY("CANCEL READY", LEGACY_EXT_IN_PROGRESS),
    CANCEL_ERROR("CANCEL ERROR", LEGACY_EXT_FAILED),

    SYSTEM_CANCELLED("SYSTEM CANCELLED", LEGACY_EXT_SYSTEM_CANCELLED),

    USER_CANCELLED("USER CANCELLED", LEGACY_EXT_USER_CANCELLED),
    USER_CANCEL_ERROR("USER CANCEL ERROR", LEGACY_EXT_USER_CANCELLED);

    private String value;
    private LegacyChargeStatus legacyStatus;

    ChargeStatus(String value, LegacyChargeStatus legacyStatus) {
        this.value = value;
        this.legacyStatus = legacyStatus;
    }

    public String getValue() {
        return value;
    }

    public String toString() {
        return this.getValue();
    }

    public LegacyChargeStatus toLegacy() {
        return legacyStatus;
    }

    public static ChargeStatus fromString(String status) {
        for (ChargeStatus stat : values()) {
            if (StringUtils.equals(stat.getValue(), status)) {
                return stat;
            }
        }
        throw new IllegalArgumentException("charge status not recognized: " + status);
    }

    public static List<ChargeStatus> fromExternal(LegacyChargeStatus legacyStatus) {
        return stream(values()).filter(status -> status.toLegacy().equals(legacyStatus)).collect(Collectors.toList());
    }
}
