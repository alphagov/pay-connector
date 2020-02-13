package uk.gov.pay.connector.common.model.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ExternalChargeRefundAvailability {

    EXTERNAL_AVAILABLE("available"),
    EXTERNAL_UNAVAILABLE("unavailable"),
    EXTERNAL_PENDING("pending"),
    EXTERNAL_FULL("full");

    private final String value;

    ExternalChargeRefundAvailability(String value) {
        this.value = value;
    }

    public String getStatus() {
        return value;
    }

    public String toString() {
        return this.value;
    }

    public static ExternalChargeRefundAvailability from(String value) {
        for (ExternalChargeRefundAvailability status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }

}
