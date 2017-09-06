package uk.gov.pay.connector.model.api;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

public enum ExternalChargeState {

    EXTERNAL_CREATED("created", false),
    EXTERNAL_STARTED("started", false),
    EXTERNAL_SUBMITTED("submitted", false),
    EXTERNAL_SUCCESS("success", true),
    EXTERNAL_FAILED_REJECTED("failed", true, "P0010", "Payment method rejected"),
    EXTERNAL_FAILED_EXPIRED("failed", true, "P0020", "Payment expired"),
    EXTERNAL_FAILED_CANCELLED("failed", true, "P0030", "Payment was cancelled by the user"),
    EXTERNAL_CANCELLED("cancelled", true, "P0040", "Payment was cancelled by the service"),
    EXTERNAL_ERROR_GATEWAY("error", true, "P0050", "Payment provider returned an error");

    private final String value;
    private final boolean finished;
    private final String code;
    private final String message;

    ExternalChargeState(String value, boolean finished) {
        this.value = value;
        this.finished = finished;
        this.code = null;
        this.message = null;
    }

    ExternalChargeState(String value, boolean finished, String code, String message) {
        this.value = value;
        this.finished = finished;
        this.code = code;
        this.message = message;
    }

    public String getStatus() {
        return value;
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
}
