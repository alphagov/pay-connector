package uk.gov.pay.connector.queue;

import uk.gov.pay.connector.events.PaymentEvent;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class PaymentStateTransition<T extends PaymentEvent> implements Delayed {
    private final long chargeEventId;
    private final Class<T> stateTransitionEventClass;
    private final Long readTime;

    private long delayDurationInMilliseconds = 100L;

    public PaymentStateTransition(long chargeEventId, Class<T> stateTransitionEventClass) {
        this.chargeEventId = chargeEventId;
        this.stateTransitionEventClass = stateTransitionEventClass;
        this.readTime = System.currentTimeMillis() + delayDurationInMilliseconds;
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

    public long getChargeEventId() {
        return chargeEventId;
    }

    public long getDelayDurationInMilliseconds() {
        return delayDurationInMilliseconds;
    }

    public Class<T> getStateTransitionEventClass() {
        return stateTransitionEventClass;
    }
}
