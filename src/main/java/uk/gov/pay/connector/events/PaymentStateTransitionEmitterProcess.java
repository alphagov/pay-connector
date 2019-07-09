package uk.gov.pay.connector.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.PaymentStateTransitionQueue;

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
        PaymentEvent paymentEvent;
        try {
            paymentEvent = createEvent(paymentStateTransition);
            eventQueue.emitEvent(paymentEvent);
        } catch (Exception e) {
            LOGGER.warn("Failed to add event to queue");
            paymentStateTransitionQueue.offer(paymentStateTransition);
        }
    }

    private PaymentEvent createEvent(PaymentStateTransition paymentStateTransition) throws Exception {
        return chargeEventDao.findById(ChargeEventEntity.class, paymentStateTransition.getChargeEventId())
                .map(chargeEvent -> createEvent(chargeEvent, paymentStateTransition.getStateTransitionEventClass()))
                .orElseThrow(() -> new Exception());
    }

    private PaymentEvent createEvent(ChargeEventEntity chargeEvent, Class<? extends PaymentEvent> paymentEventClass) {
        return null;
    }
}
