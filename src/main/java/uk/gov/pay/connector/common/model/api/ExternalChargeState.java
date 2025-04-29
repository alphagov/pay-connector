package uk.gov.pay.connector.common.model.api;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

public enum ExternalChargeState {

    EXTERNAL_CREATED("created", false),
    EXTERNAL_STARTED("started", false),
    EXTERNAL_SUBMITTED("submitted", false),
    EXTERNAL_CAPTURABLE("capturable", false),
    EXTERNAL_SUCCESS("success", true),
    EXTERNAL_FAILED_REJECTED("failed", "declined", true, "P0010", "Payment method rejected"),
    EXTERNAL_FAILED_EXPIRED("failed", "timedout", true, "P0020", "Payment expired"),
    EXTERNAL_FAILED_CANCELLED("failed", "cancelled", true, "P0030", "Payment was cancelled by the user"),
    EXTERNAL_CANCELLED("cancelled", "cancelled", true, "P0040", "Payment was cancelled by the service"),
    EXTERNAL_ERROR_GATEWAY("error", "error", true, "P0050", "Payment provider returned an error");

    private final String oldStatus;
    private final String status;
    private final boolean finished;
    private final String code;
    private final String message;

    ExternalChargeState(String status, boolean finished) {
        this.oldStatus = status;
        this.status = status;
        this.finished = finished;
        this.code = null;
        this.message = null;
    }

    ExternalChargeState(String oldStatus, String status, boolean finished, String code, String message) {
        this.oldStatus = oldStatus;
        this.status = status;
        this.finished = finished;
        this.code = code;
        this.message = message;
    }

    public String getStatus() {
        return oldStatus;
    }

    public String getStatusV2() {
        return status;
    }

    public boolean isFinished() {
        return finished;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static List<ExternalChargeState> fromStatusString(String status) {
        List<ExternalChargeState> valid = stream(values()).filter(v -> v.getStatus().equals(status)).collect(Collectors.toList());
        if (valid.isEmpty()) {
            throw new IllegalArgumentException("External charge state not recognized: " + status);
        } else {
            return valid;
        }
    }

    public static List<ExternalChargeState> fromStatusStringV2(String status) {
        if (status.equals(EXTERNAL_FAILED_REJECTED.oldStatus)) {
            return fromStatusString(status);
        }

        List<ExternalChargeState> valid = stream(values()).filter(v -> v.getStatusV2().equals(status)).collect(Collectors.toList());

        if (valid.isEmpty()) {
            throw new IllegalArgumentException("External charge state not recognized: " + status);
        } else {
            return valid;
        }
    }
}
