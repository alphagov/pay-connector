package uk.gov.pay.connector.queue.statetransition;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

/**
 * The reason for this internal queue is to prevent the state transition event being emitted if the transaction - which 
 * persists a charge_event to the database - fails and is rolled back. For example, a state transition is offered to this queue from 
 * @see uk.gov.pay.connector.charge.service.ChargeService#transitionChargeState(uk.gov.pay.connector.charge.model.domain.ChargeEntity, 
 * uk.gov.pay.connector.charge.model.domain.ChargeStatus, java.time.ZonedDateTime) which is marked with a @Transactional.
 * There are a chain of calling classes to this method that are also marked with @Transactional. It is difficult to 
 * manage the sending of the state transition event for each chain of @Transactionals in case the transaction fails and
 * needs to be rolled back. State transitions are therefore placed in this queue, to be picked up by a managed process
 * (@see uk.gov.pay.connector.queue.managed.StateTransitionMessageReceiver). If a transaction fails, there will be no 
 * charge_event in the database for the transaction and the state transition event in this queue will not get picked
 * up by the managed process and therefore not get sent to the external queue.
 */
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

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
    }
}
