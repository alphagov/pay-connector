package uk.gov.pay.connector.model.domain;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.api.ExternalChargeState;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static uk.gov.pay.connector.model.api.ExternalChargeState.*;

public enum ChargeStatus {
    CREATED("CREATED", EXTERNAL_CREATED),
    ENTERING_CARD_DETAILS("ENTERING CARD DETAILS", EXTERNAL_STARTED),
    AUTHORISATION_READY("AUTHORISATION READY", EXTERNAL_STARTED),

    AUTHORISATION_3DS_REQUIRED("AUTHORISATION 3DS REQUIRED", EXTERNAL_STARTED),
    AUTHORISATION_SUCCESS("AUTHORISATION SUCCESS", EXTERNAL_SUBMITTED),
    AUTHORISATION_REJECTED("AUTHORISATION REJECTED", EXTERNAL_FAILED_REJECTED),
    AUTHORISATION_ERROR("AUTHORISATION ERROR", EXTERNAL_ERROR_GATEWAY),

    CAPTURE_READY("CAPTURE READY", EXTERNAL_SUBMITTED),
    CAPTURED("CAPTURED", EXTERNAL_SUCCESS),
    CAPTURE_SUBMITTED("CAPTURE SUBMITTED", EXTERNAL_SUCCESS),
    CAPTURE_ERROR("CAPTURE ERROR", EXTERNAL_ERROR_GATEWAY),

    EXPIRE_CANCEL_READY("EXPIRE CANCEL READY", EXTERNAL_FAILED_EXPIRED),
    EXPIRE_CANCEL_FAILED("EXPIRE CANCEL FAILED", EXTERNAL_FAILED_EXPIRED),
    EXPIRED("EXPIRED", EXTERNAL_FAILED_EXPIRED),

    SYSTEM_CANCEL_READY("SYSTEM CANCEL READY", EXTERNAL_CANCELLED),
    SYSTEM_CANCEL_ERROR("SYSTEM CANCEL ERROR", EXTERNAL_CANCELLED),
    SYSTEM_CANCELLED("SYSTEM CANCELLED", EXTERNAL_CANCELLED),

    USER_CANCEL_READY("USER CANCEL READY", EXTERNAL_FAILED_CANCELLED),
    USER_CANCELLED("USER CANCELLED", EXTERNAL_FAILED_CANCELLED),
    USER_CANCEL_ERROR("USER CANCEL ERROR", EXTERNAL_FAILED_CANCELLED);

    private String value;
    private ExternalChargeState externalStatus;

    ChargeStatus(String value, ExternalChargeState externalStatus) {
        this.value = value;
        this.externalStatus = externalStatus;
    }

    public String getValue() {
        return value;
    }

    public String toString() {
        return this.getValue();
    }

    public ExternalChargeState toExternal() {
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

    public static List<ChargeStatus> fromExternal(ExternalChargeState externalStatus) {
        return stream(values()).filter(status -> status.toExternal().equals(externalStatus)).collect(Collectors.toList());
    }
}
