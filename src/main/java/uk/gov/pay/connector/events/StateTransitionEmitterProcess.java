package uk.gov.pay.connector.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.exception.StateTransitionMessageProcessException;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;
import uk.gov.pay.connector.events.model.charge.PaymentEventFactory;
import uk.gov.pay.connector.events.model.refund.RefundEvent;
import uk.gov.pay.connector.events.model.refund.RefundEventFactory;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.StateTransitionQueue;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.RefundStateTransition;
import uk.gov.pay.connector.queue.StateTransition;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import javax.inject.Inject;
import java.util.Optional;

public class StateTransitionEmitterProcess {
    private static final Logger LOGGER = LoggerFactory.getLogger(StateTransitionEmitterProcess.class);

    private final StateTransitionQueue stateTransitionQueue;
    private final EventQueue eventQueue;
    private final ChargeEventDao chargeEventDao;
    private final RefundDao refundDao;

    @Inject
    public StateTransitionEmitterProcess(
            StateTransitionQueue stateTransitionQueue,
            EventQueue eventQueue,
            ChargeEventDao chargeEventDao,
            RefundDao refundDao
    ) {
        this.stateTransitionQueue = stateTransitionQueue;
        this.eventQueue = eventQueue;
        this.chargeEventDao = chargeEventDao;
        this.refundDao = refundDao;
    }

    public void handleStateTransitionMessages() {
        Optional.ofNullable(stateTransitionQueue.poll())
                .ifPresent(this::emitEvent);
    }

    private void emitEvent(StateTransition stateTransition) {
        if (stateTransition.shouldAttempt()) {

            try {
                Event event = createEvent(stateTransition);
                eventQueue.emitEvent(event);
                LOGGER.info(
                        "Emitted new state transition event for [eventId={}] [eventType={}]",
                        stateTransition.getIdentifier(),
                        stateTransition.getStateTransitionEventClass().getSimpleName()
                );
            } catch (StateTransitionMessageProcessException | QueueException e) {
                LOGGER.warn(
                        "Failed to emit new event for state transition [eventId={}] [eventType={}] [error={}]",
                        stateTransition.getIdentifier(),
                        stateTransition.getStateTransitionEventClass().getSimpleName(),
                        e.getMessage()
                );
                stateTransitionQueue.offer(stateTransition.getNext());
            }
        } else {
            LOGGER.error(
                    "State transition message failed to process beyond max retries [eventId={}] [eventType={}]:",
                    stateTransition.getIdentifier(),
                    stateTransition.getStateTransitionEventClass().getSimpleName()
            );
        }
    }

    private Event createEvent(StateTransition stateTransition) throws StateTransitionMessageProcessException {
        if (stateTransition instanceof PaymentStateTransition) {
            PaymentStateTransition paymentStateTransition = (PaymentStateTransition) stateTransition;
            return chargeEventDao.findById(ChargeEventEntity.class, paymentStateTransition.getChargeEventId())
                    .map(chargeEvent -> createEvent(chargeEvent, paymentStateTransition.getStateTransitionEventClass()))
                    .orElseThrow(() -> new StateTransitionMessageProcessException(String.valueOf(paymentStateTransition.getChargeEventId())));
        } else if (stateTransition instanceof RefundStateTransition) {
            RefundStateTransition refundStateTransition = (RefundStateTransition) stateTransition;
            return refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                    refundStateTransition.getRefundExternalId(),
                    refundStateTransition.getRefundStatus())
                    .map(refundHistory -> createEvent(refundHistory, stateTransition.getStateTransitionEventClass()))
                    .orElseThrow(() -> new StateTransitionMessageProcessException(refundStateTransition.getRefundExternalId()));
        } else {
            throw new StateTransitionMessageProcessException(stateTransition.getIdentifier());
        }
    }

    private RefundEvent createEvent(RefundHistory refundHistory, Class<? extends RefundEvent> refundEventClass) {
        return RefundEventFactory.create(refundEventClass, refundHistory);

    }

    private PaymentEvent createEvent(ChargeEventEntity chargeEvent, Class<? extends PaymentEvent> paymentEventClass) {
        return PaymentEventFactory.create(paymentEventClass, chargeEvent);
    }
}
