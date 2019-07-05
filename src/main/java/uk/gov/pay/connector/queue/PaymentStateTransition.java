package uk.gov.pay.connector.queue;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class PaymentStateTransition implements Delayed {
    private final long chargeEventId;
    private final Long readTime;

    private long delayDurationInMilliseconds = 100L;

    public PaymentStateTransition(long chargeEventId) {
        this.chargeEventId = chargeEventId;
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
}
