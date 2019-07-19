package uk.gov.pay.connector.queue;

import java.util.Queue;
import java.util.concurrent.DelayQueue;

public class PaymentStateTransitionQueue {
    private final Queue<StateTransition> queue = new DelayQueue<>();
    
    public boolean offer(StateTransition paymentStateTransition) {
        return queue.offer(paymentStateTransition);
    }
    
    public StateTransition poll() {
        return queue.poll();
    }
}
