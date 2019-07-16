package uk.gov.pay.connector.queue;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public final class PaymentStateTransition implements Delayed {
    private final long chargeEventId;
    private final Class stateTransitionEventClass;
    private final Long readTime;
    private final long delayDurationInMilliseconds;
    private final int attempts;

    private static final int MAXIMUM_NUMBER_OF_ATTEMPTS = 10;
    private static final int BASE_ATTEMPTS = 1;
    private static final long DEFAULT_DELAY_DURATION_IN_MILLISECONDS = 100L;

    private PaymentStateTransition(long chargeEventId, Class stateTransitionEventClass, int numberOfProcessAttempts, long delayDurationInMilliseconds) {
        this.chargeEventId = chargeEventId;
        this.stateTransitionEventClass = stateTransitionEventClass;
        this.attempts = numberOfProcessAttempts;
        this.delayDurationInMilliseconds = delayDurationInMilliseconds;
        this.readTime = System.currentTimeMillis() + delayDurationInMilliseconds;
    }

    public PaymentStateTransition(long chargeEventId, Class stateTransitionEventClass) {
        this(chargeEventId, stateTransitionEventClass, BASE_ATTEMPTS, DEFAULT_DELAY_DURATION_IN_MILLISECONDS);
    }

    public PaymentStateTransition(long chargeEventId, Class stateTransitionEventClass, long delayDurationInMilliseconds) {
        this(chargeEventId, stateTransitionEventClass, BASE_ATTEMPTS, delayDurationInMilliseconds);
    }

    public static PaymentStateTransition incrementAttempts(PaymentStateTransition paymentStateTransition) {
        return new PaymentStateTransition(
                paymentStateTransition.getChargeEventId(),
                paymentStateTransition.getStateTransitionEventClass(),
                paymentStateTransition.getAttempts() + 1,
                paymentStateTransition.getDelayDurationInMilliseconds());
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long diff = readTime - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return (int) (readTime - ((PaymentStateTransition) o).readTime);
    }

    public boolean shouldAttempt() {
        return attempts < MAXIMUM_NUMBER_OF_ATTEMPTS;
    }

    public int getAttempts() {
        return attempts;
    }

    public long getChargeEventId() {
        return chargeEventId;
    }

    public long getDelayDurationInMilliseconds() {
        return delayDurationInMilliseconds;
    }

    public Class getStateTransitionEventClass() {
        return stateTransitionEventClass;
    }
}
