package uk.gov.pay.connector.charge.model.domain;

import uk.gov.pay.connector.common.model.Status;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;

import static org.apache.commons.lang3.Strings.CS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_CANCELLED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_CAPTURABLE;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_ERROR_GATEWAY;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_FAILED_CANCELLED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_FAILED_EXPIRED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_FAILED_REJECTED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;

public enum ChargeStatus implements Status {
    UNDEFINED("UNDEFINED", EXTERNAL_CREATED, false),
    CREATED("CREATED", EXTERNAL_CREATED, false),
    PAYMENT_NOTIFICATION_CREATED("PAYMENT NOTIFICATION CREATED", EXTERNAL_CREATED, false),
    ENTERING_CARD_DETAILS("ENTERING CARD DETAILS", EXTERNAL_STARTED, false),
    AUTHORISATION_ABORTED("AUTHORISATION ABORTED", EXTERNAL_FAILED_REJECTED, true),
    AUTHORISATION_READY("AUTHORISATION READY", EXTERNAL_STARTED, false),

    AUTHORISATION_3DS_REQUIRED("AUTHORISATION 3DS REQUIRED", EXTERNAL_STARTED, false),
    AUTHORISATION_3DS_READY("AUTHORISATION 3DS READY", EXTERNAL_STARTED, false),

    AUTHORISATION_SUBMITTED("AUTHORISATION SUBMITTED", EXTERNAL_ERROR_GATEWAY, true),
    AUTHORISATION_SUCCESS("AUTHORISATION SUCCESS", EXTERNAL_SUBMITTED, false),
    AUTHORISATION_REJECTED("AUTHORISATION REJECTED", EXTERNAL_FAILED_REJECTED, true),
    AUTHORISATION_CANCELLED("AUTHORISATION CANCELLED", EXTERNAL_FAILED_REJECTED, true),
    AUTHORISATION_ERROR("AUTHORISATION ERROR", EXTERNAL_ERROR_GATEWAY, true),
    AUTHORISATION_TIMEOUT("AUTHORISATION TIMEOUT", EXTERNAL_ERROR_GATEWAY, true),
    AUTHORISATION_UNEXPECTED_ERROR("AUTHORISATION UNEXPECTED ERROR", EXTERNAL_ERROR_GATEWAY, true),
    AWAITING_CAPTURE_REQUEST("AWAITING CAPTURE REQUEST", EXTERNAL_CAPTURABLE, false),
    CAPTURE_APPROVED("CAPTURE APPROVED", EXTERNAL_SUCCESS, false),
    CAPTURE_APPROVED_RETRY("CAPTURE APPROVED RETRY", EXTERNAL_SUCCESS, false),
    CAPTURE_READY("CAPTURE READY", EXTERNAL_SUCCESS, false),
    CAPTURED("CAPTURED", EXTERNAL_SUCCESS, true),
    CAPTURE_SUBMITTED("CAPTURE SUBMITTED", EXTERNAL_SUCCESS, false),
    CAPTURE_ERROR("CAPTURE ERROR", EXTERNAL_ERROR_GATEWAY, true),
    CAPTURE_QUEUED("CAPTURE QUEUED", EXTERNAL_SUCCESS, false),
    AUTHORISATION_USER_NOT_PRESENT_QUEUED("AUTHORISATION USER NOT PRESENT QUEUED", EXTERNAL_STARTED, false),

    EXPIRE_CANCEL_READY("EXPIRE CANCEL READY", EXTERNAL_FAILED_EXPIRED, false),
    EXPIRE_CANCEL_FAILED("EXPIRE CANCEL FAILED", EXTERNAL_FAILED_EXPIRED, true),
    EXPIRE_CANCEL_SUBMITTED("EXPIRE CANCEL SUBMITTED", EXTERNAL_FAILED_EXPIRED, false),
    EXPIRED("EXPIRED", EXTERNAL_FAILED_EXPIRED, true),

    SYSTEM_CANCEL_READY("SYSTEM CANCEL READY", EXTERNAL_CANCELLED, false),
    SYSTEM_CANCEL_ERROR("SYSTEM CANCEL ERROR", EXTERNAL_CANCELLED, true),
    SYSTEM_CANCEL_SUBMITTED("SYSTEM CANCEL SUBMITTED", EXTERNAL_CANCELLED, false),
    SYSTEM_CANCELLED("SYSTEM CANCELLED", EXTERNAL_CANCELLED, true),

    USER_CANCEL_READY("USER CANCEL READY", EXTERNAL_FAILED_CANCELLED, false),
    USER_CANCEL_SUBMITTED("USER CANCEL SUBMITTED", EXTERNAL_FAILED_CANCELLED, false),
    USER_CANCELLED("USER CANCELLED", EXTERNAL_FAILED_CANCELLED, true),
    USER_CANCEL_ERROR("USER CANCEL ERROR", EXTERNAL_FAILED_CANCELLED, true),

    // Below statuses exist to facilitate cancellation on the gateway for charges that entered the various authorisation
    // error states. A recurring cleanup job moves charges into these states when it has handled them. This job is only
    // run for ePDQ charges, so only ePDQ charges will enter these states.
    AUTHORISATION_ERROR_CANCELLED("AUTHORISATION ERROR CANCELLED", EXTERNAL_ERROR_GATEWAY, true),
    AUTHORISATION_ERROR_REJECTED("AUTHORISATION ERROR REJECTED", EXTERNAL_ERROR_GATEWAY, true),
    AUTHORISATION_ERROR_CHARGE_MISSING("AUTHORISATION ERROR CHARGE MISSING", EXTERNAL_ERROR_GATEWAY, true);

    private String value;
    private ExternalChargeState externalStatus;
    private boolean expungeable;

    ChargeStatus(String value, ExternalChargeState externalStatus, boolean expungeable) {
        this.value = value;
        this.externalStatus = externalStatus;
        this.expungeable = expungeable;
    }

    public String getValue() {
        return value;
    }

    public boolean isExpungeable() {
        return expungeable;
    }

    public String toString() {
        return this.getValue();
    }

    public ExternalChargeState toExternal() {
        return externalStatus;
    }

    public static ChargeStatus fromString(String status) {
        for (ChargeStatus stat : values()) {
            if (CS.equals(stat.getValue(), status)) {
                return stat;
            }
        }
        throw new IllegalArgumentException("charge status not recognized: " + status);
    }
}
