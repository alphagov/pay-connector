package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.domain.RefundStatus;

import static uk.gov.pay.connector.service.InterpretedStatus.Type.REFUND_STATUS;


public class MappedRefundStatus implements InterpretedStatus {

    private final RefundStatus status;

    public MappedRefundStatus(RefundStatus status) {
        this.status = status;
    }

    @Override
    public Type getType() {
        return REFUND_STATUS;
    }

    @Override
    public RefundStatus getRefundStatus() {
        return status;
    }
}
