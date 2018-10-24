package uk.gov.pay.connector.common.model.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import static java.lang.String.format;
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

    public static ExternalRefundStatus fromPublicStatusLabel(String publicStatusLabel) {
        return stream(values())
                .filter(v -> v.getStatus().equals(publicStatusLabel))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(format("External charge state not recognized: '%s'", publicStatusLabel)));
    }

    public String getStatus() {
        return value;
    }

    public boolean isFinished() {
        return finished;
    }
}
