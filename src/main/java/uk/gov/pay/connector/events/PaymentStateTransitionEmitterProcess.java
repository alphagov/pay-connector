package uk.gov.pay.connector.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.PaymentStateTransitionQueue;
import uk.gov.pay.connector.queue.QueueException;

import javax.inject.Inject;
import java.util.Optional;

public class PaymentStateTransitionEmitterProcess {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentStateTransitionEmitterProcess.class);

    private final PaymentStateTransitionQueue paymentStateTransitionQueue;
    private final EventQueue eventQueue;

    @Inject
    public PaymentStateTransitionEmitterProcess(PaymentStateTransitionQueue paymentStateTransitionQueue, EventQueue eventQueue) {
        this.paymentStateTransitionQueue = paymentStateTransitionQueue;
        this.eventQueue = eventQueue;
    }

    public void handleStateTransitionMessages() {
        // poll queue for latest message
        // try and emit event for message
        // IF successful - exit
        // IF failed - add message back to queue
        Optional.ofNullable(paymentStateTransitionQueue.poll())
                .map(this::createEvent)
                .ifPresent(this::emitEvent);

        LOGGER.info("Checking payment state transition queue for new transitions to process");
    }

    private void emitEvent(PaymentEvent paymentEvent) {
        try {
            eventQueue.emitEvent(paymentEvent);
        } catch (QueueException e) {
            LOGGER.warn("Failed to add event to queue {}", paymentEvent);
        }
    }

    private PaymentEvent createEvent(PaymentStateTransition paymentStateTransition) {
        return null;
    }
}
