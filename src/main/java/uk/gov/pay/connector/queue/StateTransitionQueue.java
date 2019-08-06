package uk.gov.pay.connector.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

public class StateTransitionQueue {
    private final BlockingQueue<StateTransition> queue = new DelayQueue<>();

    public boolean offer(StateTransition stateTransition) {
        return queue.offer(stateTransition);
    }

    public StateTransition poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public StateTransition poll() throws InterruptedException {
        return poll(0L, TimeUnit.MILLISECONDS);
    }
}
