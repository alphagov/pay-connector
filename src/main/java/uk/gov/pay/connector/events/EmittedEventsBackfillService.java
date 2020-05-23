package uk.gov.pay.connector.events;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.EmittedEventSweepConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.tasks.HistoricalEventEmitter;

import javax.inject.Inject;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static java.time.ZonedDateTime.now;

public class EmittedEventsBackfillService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    public static final int PAGE_SIZE = 100;

    private final EmittedEventDao emittedEventDao;
    private final ChargeService chargeService;
    private final HistoricalEventEmitter historicalEventEmitter;
    private RefundDao refundDao;
    private final EmittedEventSweepConfig sweepConfig;
    private static final boolean shouldForceEmission = true;

    @Inject
    public EmittedEventsBackfillService(EmittedEventDao emittedEventDao, ChargeService chargeService, RefundDao refundDao,
                                        ChargeDao chargeDao, EventService eventService,
                                        StateTransitionService stateTransitionService,
                                        ConnectorConfiguration configuration) {
        this.emittedEventDao = emittedEventDao;
        this.chargeService = chargeService;
        this.refundDao = refundDao;
        this.sweepConfig = configuration.getEmittedEventSweepConfig();
        this.historicalEventEmitter = new HistoricalEventEmitter(emittedEventDao, refundDao, chargeService, shouldForceEmission,
                eventService, stateTransitionService);
    }

    public void backfillNotEmittedEvents() {
        EmittedEventBatchIterator emittedEventBatchIterator = new EmittedEventBatchIterator(emittedEventDao, sweepConfig, 0L, PAGE_SIZE, now());

        emittedEventBatchIterator.forEachRemaining(batch -> {
            logger.info(
                    "Processing not emitted events [lastProcessedId={}, no.of.events={}, oldestDate={}]",
                    batch.getStartId(), 
                    batch.getEndId().map(Object::toString).orElse("none"), 
                    batch.oldestEventDate().map(ZonedDateTime::toString).orElse("none")
            );

            batch.getEvents().forEach(this::backfillEvent);
        });

        logger.info("Finished processing not emitted events [lastProcessedId={}, maxId={}]",
                emittedEventBatchIterator.getCurrentBatchStartId(), emittedEventBatchIterator.getMaximumIdOfEventsEligibleForReEmission().map(Object::toString).orElse("none"));
    }

    @Transactional
    public void backfillEvent(EmittedEventEntity event) {
        try {
            Optional<ChargeEntity> charge = Optional.ofNullable(chargeService.findChargeByExternalId(chargeIdForEvent(event)));

            charge.ifPresent(c -> MDC.put("chargeId", c.getExternalId()));
            charge.ifPresent(historicalEventEmitter::processPaymentAndRefundEvents);
            event.setEmittedDate(now(ZoneId.of("UTC")));
        } catch (Exception e) {
            logger.error(
                    "Failed to process backfill for event {} due to {} [externalId={}] [event_type={}] [event_date={}]",
                    event.getId(),
                    e.getMessage(),
                    event.getResourceExternalId(),
                    event.getEventType(),
                    event.getEventDate()
            );
            throw e;
        } finally {
            MDC.remove("chargeId");
        }
    }

    private String chargeIdForEvent(EmittedEventEntity event) {
        if (isPaymentEvent(event)) {
            return event.getResourceExternalId();
        } else {
            return refundDao.findByExternalId(event.getResourceExternalId())
                    .stream().findFirst()
                    .map(RefundEntity::getChargeExternalId)
                    .orElse("");
//                .orElseThrow(() -> new RuntimeException("Refund not found"));
        }
    }

    private boolean isPaymentEvent(EmittedEventEntity event) {
        return ResourceType.valueOf(event.getResourceType().toUpperCase()).equals(ResourceType.PAYMENT);
    }

}
