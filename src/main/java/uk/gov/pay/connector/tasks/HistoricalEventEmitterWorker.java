package uk.gov.pay.connector.tasks;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static uk.gov.service.payments.logging.LoggingKeys.MDC_REQUEST_ID_KEY;

public class HistoricalEventEmitterWorker {
    private static final Logger logger = LoggerFactory.getLogger(HistoricalEventEmitterWorker.class);
    private static final int PAGE_SIZE = 100;
    private final ChargeDao chargeDao;
    private final ChargeService chargeService;
    private final ChargeEventDao chargeEventDao;
    private final EmittedEventDao emittedEventDao;
    private final StateTransitionService stateTransitionService;
    private final EventService eventService;
    private final RefundDao refundDao;
    private HistoricalEventEmitter historicalEventEmitter;
    private long maxId;

    @Inject
    public HistoricalEventEmitterWorker(ChargeDao chargeDao, RefundDao refundDao, ChargeEventDao chargeEventDao,
                                        EmittedEventDao emittedEventDao, StateTransitionService stateTransitionService,
                                        EventService eventService, ChargeService chargeService) {
        this.chargeDao = chargeDao;
        this.refundDao = refundDao;
        this.chargeEventDao = chargeEventDao;
        this.emittedEventDao = emittedEventDao;
        this.stateTransitionService = stateTransitionService;
        this.eventService = eventService;
        this.chargeService = chargeService;
    }

    public void execute(Long startId, OptionalLong maybeMaxId, Long doNotRetryEmitUntilDuration) {
        try {
            MDC.put(MDC_REQUEST_ID_KEY, "HistoricalEventEmitterWorker-" + RandomUtils.nextLong(0, 10000));
            initializeHistoricalEventEmitter(doNotRetryEmitUntilDuration);
            maxId = maybeMaxId.orElseGet(chargeDao::findMaxId);
            logger.info("Starting from {} up to {}", startId, maxId);
            for (long i = startId; i <= maxId; i++) {
                emitEventsFor(i);
            }
        } catch (NullPointerException e) {
            for (StackTraceElement s : e.getStackTrace()) {
                logger.error("Null pointer exception stack trace: {}", s);
            }
            logger.error(
                    "Null pointer exception [start={}] [max={}] [error={}]",
                    startId, maxId, e);
        } catch (Exception e) {
            logger.error("Error attempting to process payment events on job [start={}] [max={}] [error={}]", startId, maxId, e);
        }

        logger.info("Terminating");
    }

    private void initializeHistoricalEventEmitter(Long doNotRetryEmitUntilDuration) {
        this.historicalEventEmitter = new HistoricalEventEmitter(emittedEventDao, refundDao, chargeService,
                eventService, stateTransitionService, doNotRetryEmitUntilDuration);
    }

    public void executeForDateRange(ZonedDateTime startDate, ZonedDateTime endDate, Long doNotRetryEmitUntilDuration) {
        MDC.put(MDC_REQUEST_ID_KEY, "HistoricalEventEmitterWorker-" + RandomUtils.nextLong(0, 10000));

        initializeHistoricalEventEmitter(doNotRetryEmitUntilDuration);
        logger.info("Starting to emit events from date range {} up to {}", startDate, endDate);

        processChargeEvents(startDate, endDate);
        processRefundEvents(startDate, endDate);
    }

    public void executeForRefundsOnly(Long startId, OptionalLong maybeMaxId, Long doNotRetryEmitUntilDuration) {
        try {
            MDC.put(MDC_REQUEST_ID_KEY, "HistoricalEventEmitterWorker-" + RandomUtils.nextLong(0, 10000));
            initializeHistoricalEventEmitter(doNotRetryEmitUntilDuration);

            maxId = maybeMaxId.orElseGet(refundDao::findMaxId);
            logger.info("Starting emitting refunds from {} up to {}", startId, maxId);
            for (long currentId = startId; currentId <= maxId; currentId++) {

                long finalCurrentId = currentId;
                refundDao.findById(currentId)
                        .ifPresentOrElse(
                                refundEntity -> historicalEventEmitter.processRefundEvents(refundEntity.getChargeExternalId(), false),
                                () -> logger.info("Refund [{}/{}] - not found", finalCurrentId, maxId));
            }
        } catch (Exception e) {
            logger.error("Error attempting to process refunds events on job [start={}] [max={}] [error={}]", startId, maxId, e);
        } finally {
            MDC.remove(MDC_REQUEST_ID_KEY);
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

                historicalEventEmitter.processPaymentEvents(charge, false);
                historicalEventEmitter.processRefundEvents(charge.getExternalId(), false);
            } else {
                logger.info("[{}/{}] - not found", currentId, maxId);
            }
        } finally {
            MDC.remove("chargeId");
        }
    }

    private void processRefundEvents(ZonedDateTime startDate, ZonedDateTime endDate) {
        int page = 1;

        while (true) {
            List<RefundHistory> refundHistoryList =
                    refundDao.getRefundHistoryByDateRange(startDate, endDate, page, PAGE_SIZE);

            if (!refundHistoryList.isEmpty()) {
                logger.info("Processing refunds events [page {}, no.of refund events {}] by date range", page, refundHistoryList.size());
                refundHistoryList
                        .stream()
                        .map(refundHistory -> refundHistory.getChargeExternalId())
                        .distinct()
                        .forEach(this::processRefundsEventsForCharge);
                page++;
            } else {
                break;
            }
        }
    }

    private void processChargeEvents(ZonedDateTime startDate, ZonedDateTime endDate) {
        int page = 1;

        while (true) {
            List<ChargeEventEntity> chargeEvents = chargeEventDao.findChargeEvents(startDate, endDate, page, PAGE_SIZE);

            if (!chargeEvents.isEmpty()) {
                logger.info("Processing charge events [page {}, no.of.events {}] by date range", page, chargeEvents.size());
                chargeEvents.stream().map(chargeEvent -> chargeEvent.getChargeEntity().getId())
                        .distinct()
                        .forEach(this::processChargeEventsForCharge);
                page++;
            } else {
                break;
            }
        }
    }

    private void processChargeEventsForCharge(Long chargeId) {
        try {
            emitEventsFor(chargeId);
        } catch (Exception e) {
            logger.error("Error attempting to process event for charge [chargeId={}] [error={}]", chargeId, e);
        }
    }

    private void processRefundsEventsForCharge(String chargeExternalId) {
        try {
            Optional<Charge> maybeCharge = chargeService.findCharge(chargeExternalId);
            maybeCharge.ifPresent(c -> MDC.put("chargeId", c.getExternalId()));

            if (maybeCharge.isPresent()) {
                final Charge charge = maybeCharge.get();
                historicalEventEmitter.processRefundEvents(charge.getExternalId(), false);
            }
        } finally {
            MDC.remove("chargeId");
        }
    }
}
