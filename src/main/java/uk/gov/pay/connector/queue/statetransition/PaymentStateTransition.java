package uk.gov.pay.connector.queue.statetransition;

public final class PaymentStateTransition extends StateTransition {
    private final long chargeEventId; 
    
    public PaymentStateTransition(long chargeEventId, Class stateTransitionEventClass) {
        super(stateTransitionEventClass);
        this.chargeEventId = chargeEventId;
    }

    public PaymentStateTransition(long chargeEventId, Class stateTransitionEventClass, long delayDurationInMilliseconds) {
        super(stateTransitionEventClass, delayDurationInMilliseconds);
        this.chargeEventId = chargeEventId;
    }

    public PaymentStateTransition(long chargeEventId, Class stateTransitionEventClass, int numberOfProcessAttempts, long delayDurationInMilliseconds) {
        super(stateTransitionEventClass, numberOfProcessAttempts, delayDurationInMilliseconds);
        this.chargeEventId = chargeEventId;
    }

    public long getChargeEventId() {
        return chargeEventId;
    }

    @Override
    public PaymentStateTransition getNext() {
        return new PaymentStateTransition(chargeEventId, getStateTransitionEventClass(), getAttempts() + 1, getDelayDurationInMilliseconds());
    }

    @Override
    public String getIdentifier() {
        return String.valueOf(chargeEventId);
    }

    @Override
    public String toString() {
        return "PaymentStateTransition{" +
                "chargeEventId=" + chargeEventId +
                "} " + super.toString();
    }
}
