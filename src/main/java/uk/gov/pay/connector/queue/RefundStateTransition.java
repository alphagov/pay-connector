package uk.gov.pay.connector.queue;

import uk.gov.pay.connector.refund.model.domain.RefundStatus;

public class RefundStateTransition extends StateTransition {
    private final String refundExternalId;
    private final RefundStatus refundStatus;
    
    
    public RefundStateTransition(String refundExternalId, RefundStatus refundStatus, Class stateTransitionEventClass) {
        super(stateTransitionEventClass);
        this.refundExternalId = refundExternalId;
        this.refundStatus = refundStatus;
    }

    public RefundStateTransition(String refundExternalId, RefundStatus refundStatus, Class stateTransitionEventClass, long delayDurationInMilliseconds) {
        super(stateTransitionEventClass, delayDurationInMilliseconds);
        this.refundExternalId = refundExternalId;
        this.refundStatus = refundStatus;
    }

    public RefundStateTransition(String refundExternalId, RefundStatus refundStatus, Class stateTransitionEventClass, int numberOfProcessAttempts, long delayDurationInMilliseconds) {
        super(stateTransitionEventClass, numberOfProcessAttempts, delayDurationInMilliseconds);
        this.refundExternalId = refundExternalId;
        this.refundStatus = refundStatus;
    }

    public String getRefundExternalId() {
        return refundExternalId;
    }

    public RefundStatus getRefundStatus() {
        return refundStatus;
    }

    @Override
    public String getIdentifier() {
        return refundExternalId + "_" + refundStatus;
    }

    @Override
    public RefundStateTransition getNext() {
        return new RefundStateTransition(refundExternalId, refundStatus, getStateTransitionEventClass(), getAttempts() + 1, getDelayDurationInMilliseconds());
    }
}
