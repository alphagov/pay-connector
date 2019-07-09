package uk.gov.pay.connector.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.exception.StateTransitionMessageProcessException;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.PaymentStateTransitionQueue;
import uk.gov.pay.connector.queue.QueueException;

import javax.inject.Inject;
import java.util.Optional;

public class PaymentStateTransitionEmitterProcess {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentStateTransitionEmitterProcess.class);

    private final PaymentStateTransitionQueue paymentStateTransitionQueue;
    private final EventQueue eventQueue;
    private final ChargeEventDao chargeEventDao;

    @Inject
    public PaymentStateTransitionEmitterProcess(
            PaymentStateTransitionQueue paymentStateTransitionQueue,
            EventQueue eventQueue,
            ChargeEventDao chargeEventDao
    ) {
        this.paymentStateTransitionQueue = paymentStateTransitionQueue;
        this.eventQueue = eventQueue;
        this.chargeEventDao = chargeEventDao;
    }

    public void handleStateTransitionMessages() {
        Optional.ofNullable(paymentStateTransitionQueue.poll())
                .ifPresent(this::emitEvent);
    }

    private void emitEvent(PaymentStateTransition paymentStateTransition) {
        if (paymentStateTransition.shouldAttempt()) {

            try {
                PaymentEvent paymentEvent = createEvent(paymentStateTransition);
                eventQueue.emitEvent(paymentEvent);
            } catch (StateTransitionMessageProcessException | QueueException e) {
                LOGGER.warn(
                        "Failed to emit payment event for state transition [chargeEventId={}] [eventType={}] [error={}]",
                        paymentStateTransition.getChargeEventId(),
                        paymentStateTransition.getStateTransitionEventClass().getSimpleName(),
                        e.getMessage()
                );
                paymentStateTransitionQueue.offer(PaymentStateTransition.incrementAttempts(paymentStateTransition));
            }
        } else {
            LOGGER.error(
                    "Payment state transition message failed to process beyond max retries [chargeEventId={}] [eventType={}]:",
                    paymentStateTransition.getChargeEventId(),
                    paymentStateTransition.getStateTransitionEventClass().getSimpleName()
            );
        }
    }

    private PaymentEvent createEvent(PaymentStateTransition paymentStateTransition) throws StateTransitionMessageProcessException {
            return chargeEventDao.findById(ChargeEventEntity.class, paymentStateTransition.getChargeEventId())
                    .map(chargeEvent -> createEvent(chargeEvent, paymentStateTransition.getStateTransitionEventClass()))
                    .orElseThrow(() -> new StateTransitionMessageProcessException(paymentStateTransition.getChargeEventId()));
    }

    private PaymentEvent createEvent(ChargeEventEntity chargeEvent, Class<? extends PaymentEvent> paymentEventClass) {
        return PaymentEventFactory.create(paymentEventClass, chargeEvent);
    }
}
