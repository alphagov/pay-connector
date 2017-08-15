package uk.gov.pay.connector.model.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collection;

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

    public static Collection<? extends ExternalRefundStatus> fromStatusString(String state) {
        return null;
    }
}
