package uk.gov.pay.connector.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.exception.StateTransitionMessageProcessException;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.PaymentEventFactory;
import uk.gov.pay.connector.events.model.refund.RefundEventFactory;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.RefundStateTransition;
import uk.gov.pay.connector.queue.StateTransition;
import uk.gov.pay.connector.queue.StateTransitionQueue;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

public class StateTransitionEmitterProcess {
    private static final Logger LOGGER = LoggerFactory.getLogger(StateTransitionEmitterProcess.class);

    private final StateTransitionQueue stateTransitionQueue;
    private final EventQueue eventQueue;
    private final RefundEventFactory refundEventFactory;
    private final ChargeEventDao chargeEventDao;

    @Inject
    public StateTransitionEmitterProcess(
            StateTransitionQueue stateTransitionQueue,
            EventQueue eventQueue,
            RefundEventFactory refundEventFactory,
            ChargeEventDao chargeEventDao
    ) {
        this.stateTransitionQueue = stateTransitionQueue;
        this.eventQueue = eventQueue;
        this.refundEventFactory = refundEventFactory;
        this.chargeEventDao = chargeEventDao;
    }

    public void handleStateTransitionMessages() {
        Optional.ofNullable(stateTransitionQueue.poll())
                .ifPresent(this::emitEvents);
    }

    private void emitEvents(StateTransition stateTransition) {
        if (stateTransition.shouldAttempt()) {
            try {
                createEvents(stateTransition)
                        .forEach(event -> {
                            try {
                                eventQueue.emitEvent(event);
                            } catch (QueueException e) {
                                handleException(e, stateTransition);
                            }
                        });
                LOGGER.info(
                        "Emitted new state transition event for [eventId={}] [eventType={}]",
                        stateTransition.getIdentifier(),
                        stateTransition.getStateTransitionEventClass().getSimpleName()
                );
            } catch (StateTransitionMessageProcessException e) {
                handleException(e, stateTransition);
            }
        } else {
            LOGGER.error(
                    "State transition message failed to process beyond max retries [eventId={}] [eventType={}]:",
                    stateTransition.getIdentifier(),
                    stateTransition.getStateTransitionEventClass().getSimpleName()
            );
        }
    }

    private void handleException(Exception e, StateTransition stateTransition) {
        LOGGER.warn(
                "Failed to emit new event for state transition [eventId={}] [eventType={}] [error={}]",
                stateTransition.getIdentifier(),
                stateTransition.getStateTransitionEventClass().getSimpleName(),
                e.getMessage()
        );
        stateTransitionQueue.offer(stateTransition.getNext());
    }

    private List<? extends Event> createEvents(StateTransition stateTransition) throws StateTransitionMessageProcessException {
        if (stateTransition instanceof PaymentStateTransition) {
            PaymentStateTransition paymentStateTransition = (PaymentStateTransition) stateTransition;
            return chargeEventDao.findById(ChargeEventEntity.class, paymentStateTransition.getChargeEventId())
                    .map(chargeEvent -> List.of(PaymentEventFactory.create(paymentStateTransition.getStateTransitionEventClass(), chargeEvent)))
                    .orElseThrow(() -> new StateTransitionMessageProcessException(String.valueOf(paymentStateTransition.getChargeEventId())));
        } else if (stateTransition instanceof RefundStateTransition) {
            RefundStateTransition refundStateTransition = (RefundStateTransition) stateTransition;
            return refundEventFactory.create(refundStateTransition);
        } else {
            throw new StateTransitionMessageProcessException(stateTransition.getIdentifier());
        }
    }
}
