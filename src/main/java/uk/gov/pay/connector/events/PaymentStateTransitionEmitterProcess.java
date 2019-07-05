package uk.gov.pay.connector.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.exception.ChargeEventNotFoundRuntimeException;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
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
        try {
            PaymentEvent paymentEvent = createEvent(paymentStateTransition);
            eventQueue.emitEvent(paymentEvent);
        } catch (ChargeEventNotFoundRuntimeException | QueueException e) {
            LOGGER.warn(
                    "Failed to emit payment event for state transition [chargeEventId={}] [eventType={}]",
                    paymentStateTransition.getChargeEventId(),
                    paymentStateTransition.getStateTransitionEventClass().getSimpleName()
            );
            paymentStateTransitionQueue.offer(paymentStateTransition);
        }
    }

    private PaymentEvent createEvent(PaymentStateTransition paymentStateTransition) throws ChargeEventNotFoundRuntimeException {
        return chargeEventDao.findById(ChargeEventEntity.class, paymentStateTransition.getChargeEventId())
                .map(chargeEvent -> createEvent(chargeEvent, paymentStateTransition.getStateTransitionEventClass()))
                .orElseThrow(() -> new ChargeEventNotFoundRuntimeException("No external charge ID"));
    }

    private PaymentEvent createEvent(ChargeEventEntity chargeEvent, Class<? extends PaymentEvent> paymentEventClass) {
        return PaymentEventFactory.create(paymentEventClass, chargeEvent);
    }
}
