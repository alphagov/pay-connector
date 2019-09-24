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
import uk.gov.pay.connector.queue.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.tasks.HistoricalEventEmitter;

import javax.inject.Inject;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static java.time.ZonedDateTime.now;

public class EmittedEventsBackfillService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final EmittedEventDao emittedEventDao;
    private final ChargeService chargeService;
    private final HistoricalEventEmitter historicalEventEmitter;
    private RefundDao refundDao;
    private final EmittedEventSweepConfig sweepConfig;
    private static final boolean shouldForceEmission = true;

    @Inject
    public EmittedEventsBackfillService(EmittedEventDao emittedEventDao, ChargeService chargeService, RefundDao refundDao,
                                        EventService eventService, StateTransitionService stateTransitionService,
                                        ConnectorConfiguration configuration) {
        this.emittedEventDao = emittedEventDao;
        this.chargeService = chargeService;
        this.refundDao = refundDao;
        this.sweepConfig = configuration.getEmittedEventSweepConfig();
        this.historicalEventEmitter = new HistoricalEventEmitter(emittedEventDao, refundDao, shouldForceEmission,
                eventService, stateTransitionService);
    }

    public void backfillNotEmittedEvents() {
        List<EmittedEventEntity> notEmittedEventsToProcess =
                emittedEventDao.findNotEmittedEventsOlderThan(getCutoffDateForProcessingNotEmittedEvents());
        String oldestEventDate = notEmittedEventsToProcess.stream()
                .map(EmittedEventEntity::getEventDate).min(ZonedDateTime::compareTo)
                .map(ZonedDateTime::toString).orElse("none");

        logger.info("Number of not emitted events to process: [{}]; oldestDate={}",
                notEmittedEventsToProcess.size(), oldestEventDate);

        notEmittedEventsToProcess.forEach(this::backfillEvent);
    }

    @Transactional
    public void backfillEvent(EmittedEventEntity event) {
        try {
            Optional<ChargeEntity> charge;

            if (ResourceType.valueOf(event.getResourceType().toUpperCase()).equals(ResourceType.PAYMENT)) {
                charge = Optional.of(chargeService.findChargeById(event.getResourceExternalId()));
            } else {
                charge = refundDao.findByExternalId(event.getResourceExternalId()).map(RefundEntity::getChargeEntity);
            }

            charge.ifPresent(c -> MDC.put("chargeId", c.getExternalId()));
            charge.ifPresent(historicalEventEmitter::processPaymentAndRefundEvents);
            event.setEmittedDate(ZonedDateTime.now(ZoneId.of("UTC")));
        } finally {
            MDC.remove("chargeId");
        }
    }

    private ZonedDateTime getCutoffDateForProcessingNotEmittedEvents() {
        int notEmittedEventMaxAgeInSeconds = sweepConfig.getNotEmittedEventMaxAgeInSeconds();
        return now().minusSeconds(notEmittedEventMaxAgeInSeconds);
    }
}
