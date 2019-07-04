package uk.gov.pay.connector.queue;

import org.junit.Test;
import uk.gov.pay.connector.events.PaymentEvent;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class PaymentStateTransitionQueueTest {
    @Test
    public void shouldNotReturnElementBeforeDelay() throws InterruptedException {
        PaymentStateTransitionQueue queue = new PaymentStateTransitionQueue();
        PaymentStateTransition transition = new PaymentStateTransition(1L, PaymentEvent.class);

        queue.offer(transition);

        PaymentStateTransition readTransition = queue.poll();

        assertNull(readTransition);
    }

    @Test
    public void shouldReturnElementAfterDelay() throws InterruptedException {
        long chargeEventId = 1L;
        long delayBufferInMilliseconds = 100L;
        PaymentStateTransitionQueue queue = new PaymentStateTransitionQueue();
        PaymentStateTransition transition = new PaymentStateTransition(chargeEventId, PaymentEvent.class);

        queue.offer(transition);

        Thread.sleep(transition.getDelayDurationInMilliseconds() + delayBufferInMilliseconds);

        PaymentStateTransition readTransition = queue.poll();

        assertThat(readTransition.getChargeEventId(), is(chargeEventId));
    }
}
