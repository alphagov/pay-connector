package uk.gov.pay.connector.queue;


import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;
import uk.gov.pay.connector.queue.statetransition.PaymentStateTransition;
import uk.gov.pay.connector.queue.statetransition.StateTransitionQueue;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

class StateTransitionQueueTest {
    @Test
    void shouldNotReturnElementBeforeDelay() throws InterruptedException {
        StateTransitionQueue queue = new StateTransitionQueue();
        PaymentStateTransition transition = new PaymentStateTransition(1L, PaymentEvent.class);

        queue.offer(transition);

        PaymentStateTransition readTransition = (PaymentStateTransition) queue.poll();

        assertNull(readTransition);
    }

    @Test
    void shouldReturnElementAfterDelay() throws InterruptedException {
        long chargeEventId = 1L;
        long delayBufferInMilliseconds = 100L;
        StateTransitionQueue queue = new StateTransitionQueue();
        PaymentStateTransition transition = new PaymentStateTransition(chargeEventId, PaymentEvent.class);

        queue.offer(transition);

        Thread.sleep(transition.getDelayDurationInMilliseconds() + delayBufferInMilliseconds);

        PaymentStateTransition readTransition = (PaymentStateTransition) queue.poll();

        assertThat(readTransition.getChargeEventId(), is(chargeEventId));
    }
}
