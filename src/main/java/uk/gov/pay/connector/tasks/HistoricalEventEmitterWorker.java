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
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.QueueException;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.filters.RestClientLoggingFilter.HEADER_REQUEST_ID;

public class HistoricalEventEmitterWorker {
    private static final Logger logger = LoggerFactory.getLogger(HistoricalEventEmitterWorker.class);

    private final ChargeDao chargeDao;
    private final EmittedEventDao emittedEventDao;
    private final EventQueue eventQueue;
    private long maxId;

    @Inject
    public HistoricalEventEmitterWorker(ChargeDao chargeDao, EmittedEventDao emittedEventDao, EventQueue eventQueue) {
        this.chargeDao = chargeDao;
        this.emittedEventDao = emittedEventDao;
        this.eventQueue = eventQueue;
    }

    public void execute(Long startId, OptionalLong maybeMaxId) {
        try {
            MDC.put(HEADER_REQUEST_ID, "HistoricalEventEmitterWorker-" + RandomUtils.nextLong(0, 10000));

            maxId = maybeMaxId.orElseGet(() -> chargeDao.findMaxId());
            logger.info("Starting from {} up to {}", startId, maxId);
            for (long i = startId; i <= maxId; i++) {
                emitEventFor(i);
            }
        } catch (Exception e) {
            logger.error("Error: {}", e);
        }

        logger.info("Terminating");
    }

    // needs to be public for transactional annotation
    @Transactional
    public void emitEventFor(long currentId) {
        final Optional<ChargeEntity> maybeCharge = chargeDao.findById(currentId);

        try {
            maybeCharge.ifPresent(c -> MDC.put("chargeId", c.getExternalId()));

            if (maybeCharge.isPresent()) {
                final ChargeEntity charge = maybeCharge.get();
                List<ChargeEventEntity> chargeEventEntities = getSortedChargeEvents(charge);
                processChargeEvents(currentId, chargeEventEntities);
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

    private void processChargeEvents(long currentId, List<ChargeEventEntity> chargeEventEntities) {
        for (int index = 0; index < chargeEventEntities.size(); index++) {
            ChargeStatus fromChargeState;
            ChargeEventEntity chargeEventEntity = chargeEventEntities.get(index);

            if (index == 0) {
                fromChargeState = ChargeStatus.UNDEFINED;
            } else {
                fromChargeState = chargeEventEntities.get(index - 1).getStatus();
            }

            processSingleChargeEvent(currentId, fromChargeState, chargeEventEntity);
        }
    }

    private void processSingleChargeEvent(long currentId, ChargeStatus fromChargeState, ChargeEventEntity chargeEventEntity) {
        PaymentGatewayStateTransitions.getInstance()
                .getEventForTransition(fromChargeState, chargeEventEntity.getStatus())
                .ifPresent(eventType -> {
                    Event event = getPaymentEvent(chargeEventEntity, eventType);
                    final boolean emittedBefore = emittedEventDao.hasBeenEmittedBefore(event);

                    if (emittedBefore) {
                        logger.info("[{}/{}] - found - emitted before", currentId, maxId);
                    } else {
                        logger.info("[{}/{}] - found - emitting {} ", currentId, maxId, event);
                        persistAndEmit(event);
                    }
                });
    }

    private Event getPaymentEvent(ChargeEventEntity chargeEventEntity, Class<Event> eventType) {
        PaymentStateTransition transition = new PaymentStateTransition(chargeEventEntity.getId(), eventType);
        return EventFactory.createPaymentEvent(chargeEventEntity, transition.getStateTransitionEventClass());
    }

    private void persistAndEmit(Event event) {
        try {
            eventQueue.emitEvent(event);
            emittedEventDao.recordEmission(event);
        } catch (QueueException e) {
            logger.error("Failed to emit event {} due to {}", event, e);
        }
    }
}
