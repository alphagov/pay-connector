package uk.gov.pay.connector.model.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

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
}
