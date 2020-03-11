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
import uk.gov.pay.connector.queue.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.tasks.HistoricalEventEmitter;

import javax.inject.Inject;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.now;

public class EmittedEventsBackfillService {
    private static final int PAGE_SIZE = 100;
    private final Logger logger = LoggerFactory.getLogger(getClass());

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
        Long lastProcessedId = 0L;
        ZonedDateTime cutoffDate = getCutoffDateForProcessingNotEmittedEvents();
        ZonedDateTime now = now(UTC);
        Optional<Long> maxId = emittedEventDao.findNotEmittedEventMaxIdOlderThan(cutoffDate, now);

        while (maxId.isPresent()) {
            List<EmittedEventEntity> notEmittedEventsToProcess =
                    emittedEventDao.findNotEmittedEventsOlderThan(cutoffDate,
                            PAGE_SIZE, lastProcessedId, maxId.get(), now);

            if (!notEmittedEventsToProcess.isEmpty()) {
                String oldestEventDate = notEmittedEventsToProcess.stream()
                        .map(EmittedEventEntity::getEventDate).min(ZonedDateTime::compareTo)
                        .map(ZonedDateTime::toString).orElse("none");

                logger.info("Processing not emitted events [lastProcessedId={}, no.of.events={}, oldestDate={}]",
                        lastProcessedId, notEmittedEventsToProcess.size(), oldestEventDate);

                notEmittedEventsToProcess.forEach(this::backfillEvent);

                lastProcessedId = notEmittedEventsToProcess.get(notEmittedEventsToProcess.size() - 1).getId();
            } else {
                break;
            }
        }
        logger.info("Finished processing not emitted events [lastProcessedId={}, maxId={}]",
                lastProcessedId, maxId.map(Object::toString).orElse("none"));
    }

    @Transactional
    public void backfillEvent(EmittedEventEntity event) {
        try {
            ChargeEntity charge = null;

            if (ResourceType.valueOf(event.getResourceType().toUpperCase()).equals(ResourceType.PAYMENT)) {
                charge = chargeService.findChargeByExternalId(event.getResourceExternalId());
            } else {
                Optional<RefundEntity> refundEntity = refundDao.findByExternalId(event.getResourceExternalId())
                        .stream()
                        .findFirst();
                if (refundEntity.isPresent()) {
                    charge = chargeService.findChargeByExternalId(refundEntity.get().getChargeExternalId());
                }
            }

            MDC.put("chargeId", charge.getExternalId());
            historicalEventEmitter.processPaymentAndRefundEvents(charge);
            event.setEmittedDate(now(ZoneId.of("UTC")));
        } finally {
            MDC.remove("chargeId");
        }
    }

    private ZonedDateTime getCutoffDateForProcessingNotEmittedEvents() {
        int notEmittedEventMaxAgeInSeconds = sweepConfig.getNotEmittedEventMaxAgeInSeconds();
        return now().minusSeconds(notEmittedEventMaxAgeInSeconds);
    }
}
