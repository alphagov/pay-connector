package uk.gov.pay.connector.refund.model.domain;

import org.apache.commons.lang3.Strings;
import uk.gov.pay.connector.common.model.Status;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static uk.gov.pay.connector.common.model.api.ExternalRefundStatus.EXTERNAL_ERROR;
import static uk.gov.pay.connector.common.model.api.ExternalRefundStatus.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.common.model.api.ExternalRefundStatus.EXTERNAL_SUCCESS;

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
            if (Strings.CS.equals(stat.getValue(), status)) {
                return stat;
            }
        }
        throw new IllegalArgumentException("Refund status not recognized: " + status);
    }

    public static List<RefundStatus> fromExternal(ExternalRefundStatus externalStatus) {
        return stream(values()).filter(status -> status.toExternal().equals(externalStatus)).collect(Collectors.toList());
    }
}
