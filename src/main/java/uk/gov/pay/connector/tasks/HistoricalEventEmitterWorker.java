package uk.gov.pay.connector.tasks;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.charge.dao.ChargeDao;
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
import uk.gov.pay.connector.queue.StateTransitionQueue;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
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
import static uk.gov.pay.connector.filters.RestClientLoggingFilter.HEADER_REQUEST_ID;

public class HistoricalEventEmitterWorker {
    private static final Logger logger = LoggerFactory.getLogger(HistoricalEventEmitterWorker.class);
    private final ChargeDao chargeDao;
    private final EmittedEventDao emittedEventDao;
    private StateTransitionQueue stateTransitionQueue;
    private final EventQueue eventQueue;
    
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
    
    private long maxId;
    private PaymentGatewayStateTransitions paymentGatewayStateTransitions;

    @Inject
    public HistoricalEventEmitterWorker(ChargeDao chargeDao, EmittedEventDao emittedEventDao,
                                        StateTransitionQueue stateTransitionQueue, EventQueue eventQueue) {
        this.chargeDao = chargeDao;
        this.emittedEventDao = emittedEventDao;
        this.stateTransitionQueue = stateTransitionQueue;
        this.eventQueue = eventQueue;
        this.paymentGatewayStateTransitions = PaymentGatewayStateTransitions.getInstance();
    }

    public void execute(Long startId, OptionalLong maybeMaxId) {
        try {
            MDC.put(HEADER_REQUEST_ID, "HistoricalEventEmitterWorker-" + RandomUtils.nextLong(0, 10000));

            maxId = maybeMaxId.orElseGet(() -> chargeDao.findMaxId());
            logger.info("Starting from {} up to {}", startId, maxId);
            for (long i = startId; i <= maxId; i++) {
                emitEventsFor(i);
            }
        } catch (Exception e) {
            logger.error("Error attempting to process payment events on job [start={}] [max={}] [error={}]", startId, maxId, e);
        }

        logger.info("Terminating");
    }

    // needs to be public for transactional annotation
    @Transactional
    public void emitEventsFor(long currentId) {
        final Optional<ChargeEntity> maybeCharge = chargeDao.findById(currentId);

        try {
            maybeCharge.ifPresent(c -> MDC.put("chargeId", c.getExternalId()));

            if (maybeCharge.isPresent()) {
                final ChargeEntity charge = maybeCharge.get();
                List<ChargeEventEntity> chargeEventEntities = getSortedChargeEvents(charge);
                processChargeStateTransitionEvents(currentId, chargeEventEntities);
                processPaymentDetailEnteredEvent(chargeEventEntities);
            } else {
                logger.info("[{}/{}] - not found", currentId, maxId);
            }
        } finally {
            MDC.remove("chargeId");
        }
    }

    private List<ChargeEventEntity> getSortedChargeEvents(ChargeEntity charge) {
        return charge.getEvents()
                .stream()
                .sorted(Comparator.comparing(ChargeEventEntity::getUpdated))
                .collect(Collectors.toList());
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

        final boolean emittedBefore = emittedEventDao.hasBeenEmittedBefore(event);

        if (emittedBefore) {
            logger.info("[{}/{}] - found - charge event [{}] emitted before", currentId, maxId, chargeEventEntity.getId());
        } else {
            logger.info("[{}/{}] - found - emitting {} for charge event [{}] ", currentId, maxId, event, chargeEventEntity.getId());
            stateTransitionQueue.offer(transition);
            persistEventEmittedRecord(event);
        }
    }

    private void processPaymentDetailEnteredEvent(List<ChargeEventEntity> chargeEventEntities) {
        // transition to AUTHORISATION_READY does not record state transition, verify details have been entered by
        // checking against any terminal authentication transition
        chargeEventEntities
                .stream()
                .filter(event -> TERMINAL_AUTHENTICATION_STATES.contains(event.getStatus()))
                .map(PaymentDetailsEntered::from)
                .filter(event -> !emittedEventDao.hasBeenEmittedBefore(event))
                .forEach(this::emitAndPersistEvent);
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
