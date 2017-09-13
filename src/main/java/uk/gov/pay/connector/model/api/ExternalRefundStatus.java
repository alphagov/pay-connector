package uk.gov.pay.connector.model.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ExternalRefundStatus {

    EXTERNAL_SUBMITTED("submitted", false),
    EXTERNAL_SUCCESS("success", true),
    EXTERNAL_ERROR("error", true);

    private final String value;
    private final boolean finished;

    ExternalRefundStatus(String value, boolean finished) {
        this.value = value;
        this.finished = finished;
    }

    public String getStatus() {
        return value;
    }

    public boolean isFinished() {
        return finished;
    }

    public static List<ExternalRefundStatus> fromStatusString(String status) {
        List<ExternalRefundStatus> valid = stream(values()).filter(v -> v.getStatus().equals(status)).collect(Collectors.toList());

        if (valid.isEmpty()) {
            throw new IllegalArgumentException("External charge state not recognized: " + status);
        }
        else {
            return valid;
        }
    }
}
