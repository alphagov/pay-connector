package uk.gov.pay.connector.queue;

import java.util.Queue;
import java.util.concurrent.DelayQueue;

public class PaymentStateTransitionQueue {
    private final Queue<PaymentStateTransition> queue = new DelayQueue<>();
    
    public boolean offer(PaymentStateTransition paymentStateTransition) {
        return queue.offer(paymentStateTransition);
    }
    
    public PaymentStateTransition poll() {
        return queue.poll();
    }
}
