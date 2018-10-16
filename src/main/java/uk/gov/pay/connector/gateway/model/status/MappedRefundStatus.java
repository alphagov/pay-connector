package uk.gov.pay.connector.gateway.model.status;

import uk.gov.pay.connector.refund.model.domain.RefundStatus;


public class MappedRefundStatus implements InterpretedStatus {

    private final RefundStatus status;

    public MappedRefundStatus(RefundStatus status) {
        this.status = status;
    }

    @Override
    public Type getType() {
        return Type.REFUND_STATUS;
    }

    @Override
    public RefundStatus getRefundStatus() {
        return status;
    }
}
