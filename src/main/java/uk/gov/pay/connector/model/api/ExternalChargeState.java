package uk.gov.pay.connector.model.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ExternalChargeState {
    EXTERNAL_CREATED("created", false, null),

    EXTERNAL_STARTED("started", false, null),
    EXTERNAL_SUBMITTED("submitted", false, null),

    EXTERNAL_CONFIRMED("confirmed", true, true),
    EXTERNAL_FAILED_REJECTED("failed", true, false, "P0010", "Payment method rejected"),
    EXTERNAL_FAILED_EXPIRED("failed", true, false, "P0020", "Payment expired"),
    EXTERNAL_FAILED_CANCELLED("failed", true, false, "P0030", "Payment was cancelled by the user"),
    EXTERNAL_CANCELLED("cancelled", true, false, "P0040", "Payment was cancelled by the service"),
    EXTERNAL_ERROR_GATEWAY("error", true, false, "P0050", "Payment provider returned an error"),

    EXTERNAL_CAPTURED("captured", true, true),

    ;

    private final String value;
    private final boolean finished;
    private final String code;
    private final String message;

    ExternalChargeState(String value, boolean finished, Boolean success) {
        this.value = value;
        this.finished = finished;
        this.code = null;
        this.message = null;
    }

    ExternalChargeState(String value, boolean finished, Boolean success, String code, String message) {
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
        // temporary code to allow migration from Confirmed/Captured to Success 
        if ("success".equals(status)) {
            return Arrays.asList(EXTERNAL_CONFIRMED, EXTERNAL_CAPTURED);
        }
      
        List<ExternalChargeState> valid = stream(values()).filter(v -> v.getStatus().equals(status)).collect(Collectors.toList());

        if (valid.isEmpty()) {
            throw new IllegalArgumentException("External charge state not recognized: " + status);
        }
        else {
            return valid;
        }
    }
}
