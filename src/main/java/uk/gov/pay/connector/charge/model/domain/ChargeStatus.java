package uk.gov.pay.connector.charge.model.domain;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.Status;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CANCELLED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_ERROR_GATEWAY;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_FAILED_CANCELLED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_FAILED_EXPIRED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_FAILED_REJECTED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUCCESS;

public enum ChargeStatus implements Status {
    CREATED("CREATED", EXTERNAL_CREATED),
    ENTERING_CARD_DETAILS("ENTERING CARD DETAILS", EXTERNAL_STARTED),
    AUTHORISATION_ABORTED("AUTHORISATION ABORTED", EXTERNAL_FAILED_REJECTED),
    AUTHORISATION_READY("AUTHORISATION READY", EXTERNAL_STARTED),

    AUTHORISATION_3DS_REQUIRED("AUTHORISATION 3DS REQUIRED", EXTERNAL_STARTED),
    AUTHORISATION_3DS_READY("AUTHORISATION 3DS READY", EXTERNAL_STARTED),


    AUTHORISATION_SUBMITTED("AUTHORISATION SUBMITTED", EXTERNAL_ERROR_GATEWAY),
    AUTHORISATION_SUCCESS("AUTHORISATION SUCCESS", EXTERNAL_SUBMITTED),
    AUTHORISATION_REJECTED("AUTHORISATION REJECTED", EXTERNAL_FAILED_REJECTED),
    AUTHORISATION_CANCELLED("AUTHORISATION CANCELLED", EXTERNAL_FAILED_REJECTED),
    AUTHORISATION_ERROR("AUTHORISATION ERROR", EXTERNAL_ERROR_GATEWAY),
    AUTHORISATION_TIMEOUT("AUTHORISATION TIMEOUT", EXTERNAL_ERROR_GATEWAY),
    AUTHORISATION_UNEXPECTED_ERROR("AUTHORISATION UNEXPECTED ERROR", EXTERNAL_ERROR_GATEWAY),
    AWAITING_CAPTURE_REQUEST("AWAITING CAPTURE REQUEST", EXTERNAL_SUBMITTED),
    CAPTURE_APPROVED("CAPTURE APPROVED", EXTERNAL_SUCCESS),
    CAPTURE_APPROVED_RETRY("CAPTURE APPROVED RETRY", EXTERNAL_SUCCESS),
    CAPTURE_READY("CAPTURE READY", EXTERNAL_SUCCESS),
    CAPTURED("CAPTURED", EXTERNAL_SUCCESS),
    CAPTURE_SUBMITTED("CAPTURE SUBMITTED", EXTERNAL_SUCCESS),
    CAPTURE_ERROR("CAPTURE ERROR", EXTERNAL_ERROR_GATEWAY),

    EXPIRE_CANCEL_READY("EXPIRE CANCEL READY", EXTERNAL_FAILED_EXPIRED),
    EXPIRE_CANCEL_FAILED("EXPIRE CANCEL FAILED", EXTERNAL_FAILED_EXPIRED),
    EXPIRE_CANCEL_SUBMITTED("EXPIRE CANCEL SUBMITTED", EXTERNAL_FAILED_EXPIRED),
    EXPIRED("EXPIRED", EXTERNAL_FAILED_EXPIRED),

    SYSTEM_CANCEL_READY("SYSTEM CANCEL READY", EXTERNAL_CANCELLED),
    SYSTEM_CANCEL_ERROR("SYSTEM CANCEL ERROR", EXTERNAL_CANCELLED),
    SYSTEM_CANCEL_SUBMITTED("SYSTEM CANCEL SUBMITTED", EXTERNAL_CANCELLED),
    SYSTEM_CANCELLED("SYSTEM CANCELLED", EXTERNAL_CANCELLED),

    USER_CANCEL_READY("USER CANCEL READY", EXTERNAL_FAILED_CANCELLED),
    USER_CANCEL_SUBMITTED("USER CANCEL SUBMITTED", EXTERNAL_FAILED_CANCELLED),
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
