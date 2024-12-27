package uk.gov.pay.connector.events;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.EmittedEventSweepConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.tasks.HistoricalEventEmitter;

import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.now;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class EmittedEventsBackfillService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    public static final int PAGE_SIZE = 100;

    private final EmittedEventDao emittedEventDao;
    private final ChargeService chargeService;
    private final HistoricalEventEmitter historicalEventEmitter;
    private RefundDao refundDao;
    private final EmittedEventSweepConfig sweepConfig;
    private long doNotRetryEmittingEventUntilDurationInSeconds;

    @Inject
    public EmittedEventsBackfillService(EmittedEventDao emittedEventDao, ChargeService chargeService, RefundDao refundDao,
                                        HistoricalEventEmitter historicalEventEmitter, ConnectorConfiguration configuration) {
        this.emittedEventDao = emittedEventDao;
        this.chargeService = chargeService;
        this.refundDao = refundDao;
        this.sweepConfig = configuration.getEmittedEventSweepConfig();
        this.doNotRetryEmittingEventUntilDurationInSeconds = configuration.getEventEmitterConfig()
                .getDefaultDoNotRetryEmittingEventUntilDurationInSeconds();
        this.historicalEventEmitter = historicalEventEmitter;
    }

    public void backfillNotEmittedEvents() {
        EmittedEventBatchIterator emittedEventBatchIterator = new EmittedEventBatchIterator(emittedEventDao, sweepConfig, 0L, PAGE_SIZE, now());

        emittedEventBatchIterator.forEachRemaining(batch -> {
            logger.info(
                    "Processing not emitted events [lastProcessedId={}, no.of.events={}, oldestDate={}]",
                    batch.getStartId(),
                    batch.getEndId().map(Object::toString).orElse("none"),
                    batch.oldestEventDate().map(Instant::toString).orElse("none")
            );

            batch.getEvents().forEach(this::backfillEvent);
        });

        logger.info("Finished processing not emitted events [lastProcessedId={}, maxId={}]",
                emittedEventBatchIterator.getCurrentBatchStartId(), emittedEventBatchIterator
                        .getMaximumIdOfEventsEligibleForReEmission().map(Object::toString).orElse("none"));
    }

    @Transactional
    public void backfillEvent(EmittedEventEntity event) {
        try {
            String chargeId = chargeIdForEvent(event);

            MDC.put(PAYMENT_EXTERNAL_ID, chargeId);
            if (isPaymentEvent(event)) {
                ChargeEntity chargeEntity = chargeService.findChargeByExternalId(chargeId);
                historicalEventEmitter.processPaymentEvents(chargeEntity, true);
            } else {
                historicalEventEmitter.emitEventsForRefund(event.getResourceExternalId(), true);
            }
            event.setEmittedDate(Instant.now());
        } catch (Exception e) {
            logger.error(
                    "Failed to process backfill for event {} due to {} [externalId={}] [event_type={}] [event_date={}]",
                    event.getId(),
                    e.getMessage(),
                    event.getResourceExternalId(),
                    event.getEventType(),
                    event.getEventDate()
            );
            event.setDoNotRetryEmitUntil(ZonedDateTime.now(UTC).plusSeconds(doNotRetryEmittingEventUntilDurationInSeconds));
        } finally {
            MDC.remove(PAYMENT_EXTERNAL_ID);
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
        }
    }

    private boolean isPaymentEvent(EmittedEventEntity event) {
        return ResourceType.valueOf(event.getResourceType().toUpperCase()).equals(ResourceType.PAYMENT);
    }

}
