package uk.gov.pay.connector.model.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ExternalRefundStatus {

    EXTERNAL_SUBMITTED("submitted"),
    EXTERNAL_SUCCESS("success"),
    EXTERNAL_ERROR("error");

    private final String value;

    ExternalRefundStatus(String value) {
        this.value = value;
    }

    public String getStatus() {
        return value;
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
