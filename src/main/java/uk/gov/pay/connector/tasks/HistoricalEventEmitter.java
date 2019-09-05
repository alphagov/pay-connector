package uk.gov.pay.connector.tasks;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions;
import uk.gov.pay.connector.events.EventQueue;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.EventFactory;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.RefundStateTransition;
import uk.gov.pay.connector.queue.StateTransitionQueue;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.service.RefundStateEventMap;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_READY;

public class HistoricalEventEmitter {
    private static final Logger logger = LoggerFactory.getLogger(HistoricalEventEmitter.class);
    private final EmittedEventDao emittedEventDao;
    private StateTransitionQueue stateTransitionQueue;
    private final EventQueue eventQueue;
    private final RefundDao refundDao;
    private boolean shouldForceEmission;

    private final List<ChargeStatus> TERMINAL_AUTHENTICATION_STATES = List.of(
            AUTHORISATION_3DS_REQUIRED,
            AUTHORISATION_SUBMITTED,
            AUTHORISATION_SUCCESS,
            AUTHORISATION_ABORTED,
            AUTHORISATION_REJECTED,
            AUTHORISATION_ERROR,
            AUTHORISATION_UNEXPECTED_ERROR,
            AUTHORISATION_TIMEOUT,
            AUTHORISATION_CANCELLED
    );
    private static final List<ChargeStatus> INTERMEDIATE_READY_STATES = List.of(AUTHORISATION_3DS_READY,
            AUTHORISATION_READY,
            CAPTURE_READY,
            EXPIRE_CANCEL_READY,
            SYSTEM_CANCEL_READY,
            USER_CANCEL_READY);

    private PaymentGatewayStateTransitions paymentGatewayStateTransitions;

    @Inject
    public HistoricalEventEmitter(EmittedEventDao emittedEventDao,
                                  StateTransitionQueue stateTransitionQueue, EventQueue eventQueue,
                                  RefundDao refundDao) {
        this(emittedEventDao, stateTransitionQueue, eventQueue, refundDao, false);
    }

    public HistoricalEventEmitter(EmittedEventDao emittedEventDao,
                                  StateTransitionQueue stateTransitionQueue, EventQueue eventQueue,
                                  RefundDao refundDao, boolean shouldForceEmission) {
        this.emittedEventDao = emittedEventDao;
        this.stateTransitionQueue = stateTransitionQueue;
        this.eventQueue = eventQueue;
        this.refundDao = refundDao;
        this.paymentGatewayStateTransitions = PaymentGatewayStateTransitions.getInstance();
        this.shouldForceEmission = shouldForceEmission;
    }

    public void processPaymentEvents(ChargeEntity charge) {
        List<ChargeEventEntity> chargeEventEntities = getSortedChargeEvents(charge);

        processChargeStateTransitionEvents(charge.getId(), chargeEventEntities);
        processPaymentDetailEnteredEvent(chargeEventEntities);
    }

    @Transactional
    public void processRefundEvents(ChargeEntity charge) {
        List<RefundHistory> refundHistories = refundDao.searchAllHistoryByChargeId(charge.getId());

        refundHistories
                .stream()
                .sorted(Comparator.comparing(RefundHistory::getHistoryStartDate))
                .forEach(this::emitAndPersistEventForRefundHistoryEntry);
    }

    public void emitAndPersistEventForRefundHistoryEntry(RefundHistory refundHistory) {
        Class refundEventClass = RefundStateEventMap.calculateRefundEventClass(refundHistory.getUserExternalId(), refundHistory.getStatus());
        Event event = EventFactory.createRefundEvent(refundHistory, refundEventClass);

        if (shouldForceEmission) {
            emitRefundEvent(refundHistory, refundEventClass, event);
        } else {
            boolean emittedBefore = emittedEventDao.hasBeenEmittedBefore(event);

            if (emittedBefore) {
                logger.info("Refund history event emitted before [refundExternalId={}] [refundHistoryId={}]", refundHistory.getExternalId(), refundHistory.getId());
            } else {
                emitRefundEvent(refundHistory, refundEventClass, event);
            }
        }
    }

    private void emitRefundEvent(RefundHistory refundHistory, Class refundEventClass, Event event) {
        RefundStateTransition stateTransition = new RefundStateTransition(
                refundHistory.getExternalId(),
                refundHistory.getStatus(),
                refundEventClass);

        logger.info("Processing new refund history event: [refundExternalId={}] [refundHistoryId={}]", refundHistory.getExternalId(), refundHistory.getId());

        stateTransitionQueue.offer(stateTransition);
        persistEventEmittedRecord(event);
    }

    private List<ChargeEventEntity> getSortedChargeEvents(ChargeEntity charge) {
        return charge.getEvents()
                .stream()
                .sorted(Comparator.comparing(ChargeEventEntity::getUpdated))
                .sorted(HistoricalEventEmitter::sortOutOfOrderCaptureEvents)
                .collect(Collectors.toList());
    }

    private static int sortOutOfOrderCaptureEvents(ChargeEventEntity lhs, ChargeEventEntity rhs) {
        // puts CAPTURE_SUBMITTED at top of the events list (after first pass of sorting)
        // when timestamp for CAPTURED is same or before CAPTURE_SUBMITTED timestamp 
        if (lhs.getStatus().equals(ChargeStatus.CAPTURE_SUBMITTED)
                && rhs.getStatus().equals(ChargeStatus.CAPTURED)) {
            return -1;
        } else {
            return 0;
        }
    }

    private void processChargeStateTransitionEvents(long currentId, List<ChargeEventEntity> chargeEventEntities) {
        for (int index = 0; index < chargeEventEntities.size(); index++) {
            ChargeStatus fromChargeState;
            ChargeEventEntity chargeEventEntity = chargeEventEntities.get(index);

            if (index == 0) {
                fromChargeState = ChargeStatus.UNDEFINED;
            } else {
                fromChargeState = chargeEventEntities.get(index - 1).getStatus();
            }

            processSingleChargeStateTransitionEvent(currentId, fromChargeState, chargeEventEntity);
        }
    }

    private void processSingleChargeStateTransitionEvent(long currentId, ChargeStatus fromChargeState,
                                                         ChargeEventEntity chargeEventEntity) {
        Optional<Class<Event>> eventForTransition = getEventForTransition(fromChargeState, chargeEventEntity);

        eventForTransition.ifPresent(eventType -> {
            PaymentStateTransition transition = new PaymentStateTransition(chargeEventEntity.getId(), eventType);
            offerPaymentStateTransitionEvents(currentId, chargeEventEntity, transition);
        });
    }

    private Optional<Class<Event>> getEventForTransition(ChargeStatus fromChargeStatus,
                                                         ChargeEventEntity chargeEventEntity) {
        Optional<Class<Event>> eventForTransition = paymentGatewayStateTransitions
                .getEventForTransition(fromChargeStatus, chargeEventEntity.getStatus());

        if (eventForTransition.isEmpty()) {
            Optional<ChargeStatus> intermediateChargeStatus = paymentGatewayStateTransitions
                    .getIntermediateChargeStatus(fromChargeStatus, chargeEventEntity.getStatus());

            if (intermediateChargeStatus.isPresent() && INTERMEDIATE_READY_STATES.contains(intermediateChargeStatus.get())) {
                eventForTransition = paymentGatewayStateTransitions
                        .getEventForTransition(intermediateChargeStatus.get(), chargeEventEntity.getStatus());
            } else {
                logger.info("Historical Event emitter for Charge [{}] and Event [{}] - Couldn't derive event for transition from [{}] to [{}]",
                        chargeEventEntity.getChargeEntity().getId(),
                        chargeEventEntity.getId(),
                        fromChargeStatus,
                        chargeEventEntity.getStatus()
                );
            }
        }
        return eventForTransition;
    }

    private void offerPaymentStateTransitionEvents(long currentId, ChargeEventEntity chargeEventEntity, PaymentStateTransition transition) {
        Event event = EventFactory.createPaymentEvent(chargeEventEntity, transition.getStateTransitionEventClass());

        if (shouldForceEmission) {
            offerStateTransitionEvent(currentId, chargeEventEntity, transition, event);
        } else {
            final boolean emittedBefore = emittedEventDao.hasBeenEmittedBefore(event);

            if (emittedBefore) {
                logger.info("[{}] - found - charge event [{}] emitted before", currentId, chargeEventEntity.getId());
            } else {
                offerStateTransitionEvent(currentId, chargeEventEntity, transition, event);
            }
        }
    }

    private void offerStateTransitionEvent(long currentId, ChargeEventEntity chargeEventEntity, PaymentStateTransition transition, Event event) {
        logger.info("[{}] - found - emitting {} for charge event [{}] ", currentId, event, chargeEventEntity.getId());
        stateTransitionQueue.offer(transition);
        persistEventEmittedRecord(event);
    }

    private void processPaymentDetailEnteredEvent(List<ChargeEventEntity> chargeEventEntities) {
        // transition to AUTHORISATION_READY does not record state transition, verify details have been entered by
        // checking against any terminal authentication transition
        chargeEventEntities
                .stream()
                .filter(event -> isValidPaymentDetailsEnteredTransition(chargeEventEntities, event))
                .map(PaymentDetailsEntered::from)
                .filter(event -> shouldForceEmission || !emittedEventDao.hasBeenEmittedBefore(event))
                .forEach(this::emitAndPersistEvent);
    }

    private boolean isValidPaymentDetailsEnteredTransition(List<ChargeEventEntity> chargeEventEntities, ChargeEventEntity event) {
        return TERMINAL_AUTHENTICATION_STATES.contains(event.getStatus())
                && isNotDuplicatePaymentDetailsEvent(chargeEventEntities, event);
    }

    private boolean isNotDuplicatePaymentDetailsEvent(List<ChargeEventEntity> chargeEventEntities, ChargeEventEntity event) {
        return !event.getStatus().equals(AUTHORISATION_SUCCESS)
                || event.getStatus().equals(AUTHORISATION_SUCCESS)
                && !chargeEventEntities
                .stream()
                .map(ChargeEventEntity::getStatus)
                .collect(Collectors.toList()).contains(AUTHORISATION_3DS_REQUIRED);
    }

    private void emitAndPersistEvent(PaymentDetailsEntered event) {
        try {
            eventQueue.emitEvent(event);
            persistEventEmittedRecord(event);
        } catch (QueueException e) {
            logger.error("Failed to emit event {} due to {} [chargeId={}]", event, e.getMessage(), event.getResourceExternalId());
        }
    }

    private void persistEventEmittedRecord(Event event) {
        emittedEventDao.recordEmission(event);
    }
}
