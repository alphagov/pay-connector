package uk.gov.pay.connector.queue;

import java.util.Queue;
import java.util.concurrent.DelayQueue;

public class StateTransitionQueue {
    private final Queue<StateTransition> queue = new DelayQueue<>();
    
    public boolean offer(StateTransition stateTransition) {
        return queue.offer(stateTransition);
    }
    
    public StateTransition poll() {
        return queue.poll();
    }
}
