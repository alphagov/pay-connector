package uk.gov.pay.connector.model.domain;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.api.ExternalRefundStatus;

import static uk.gov.pay.connector.model.api.ExternalRefundStatus.*;

public enum RefundStatus implements Status {

    CREATED("CREATED", EXTERNAL_SUBMITTED),
    REFUND_SUBMITTED("REFUND SUBMITTED", EXTERNAL_SUBMITTED),
    REFUND_ERROR("REFUND ERROR", EXTERNAL_ERROR),
    REFUNDED("REFUNDED", EXTERNAL_SUCCESS);

    private String value;
    private ExternalRefundStatus externalStatus;

    RefundStatus(String value, ExternalRefundStatus externalStatus) {
        this.value = value;
        this.externalStatus = externalStatus;
    }

    public String getValue() {
        return value;
    }

    public String toString() {
        return this.getValue();
    }

    public ExternalRefundStatus toExternal() {
        return externalStatus;
    }

    public static RefundStatus fromString(String status) {
        for (RefundStatus stat : values()) {
            if (StringUtils.equals(stat.getValue(), status)) {
                return stat;
            }
        }
        throw new IllegalArgumentException("Refund status not recognized: " + status);
    }
}
