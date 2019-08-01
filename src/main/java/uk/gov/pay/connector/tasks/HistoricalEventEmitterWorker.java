package uk.gov.pay.connector.tasks;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.EventQueue;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.queue.QueueException;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static uk.gov.pay.connector.filters.RestClientLoggingFilter.HEADER_REQUEST_ID;

public class HistoricalEventEmitterWorker {
    private static final Logger logger = LoggerFactory.getLogger(HistoricalEventEmitterWorker.class);

    private final ChargeDao chargeDao;
    private final EmittedEventDao emittedEventDao;
    private final ChargeEventDao chargeEventDao;
    private final EventQueue eventQueue;
    private long maxId;

    @Inject
    public HistoricalEventEmitterWorker(ChargeDao chargeDao, EmittedEventDao emittedEventDao,
                                        ChargeEventDao chargeEventDao, EventQueue eventQueue) {
        this.chargeDao = chargeDao;
        this.emittedEventDao = emittedEventDao;
        this.chargeEventDao = chargeEventDao;
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
    public void emitEventFor(long i) {
        final Optional<ChargeEntity> maybeCharge = chargeDao.findById(i);

        try {
            maybeCharge.ifPresent((c) -> MDC.put("chargeId", c.getExternalId()) );

            if (maybeCharge.isPresent()) {
                final ChargeEntity charge = maybeCharge.get();
                List<ChargeEventEntity> chargeEventEntities = chargeEventDao.findEventsByChargeId(charge.getId());

                final PaymentCreated event = PaymentCreated.from(charge);
                final boolean emittedBefore = emittedEventDao.hasBeenEmittedBefore(event);
                if (emittedBefore) {
                    logger.info("[{}/{}] - found - emitted before", i, maxId);
                } else {
                    logger.info("[{}/{}] - found - emitting {}", i, maxId, event);
                    persistAndEmit(event);
                }
            }
            else {
                logger.info("[{}/{}] - not found", i, maxId);
            }
        }
        finally {
            MDC.remove("chargeId");
        }
    }

    private void persistAndEmit(PaymentCreated event) {
        try {
            eventQueue.emitEvent(event);
            emittedEventDao.recordEmission(event);
        } catch (QueueException e) {
            logger.error("Failed to emit event {} due to {}", event, e);
        }
    }
}
