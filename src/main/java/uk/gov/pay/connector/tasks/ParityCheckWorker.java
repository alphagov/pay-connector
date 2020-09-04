package uk.gov.pay.connector.tasks;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.filters.RestClientLoggingFilter.HEADER_REQUEST_ID;

public class ParityCheckWorker {
    private static final int PAGE_SIZE = 100;
    private static final Logger logger = LoggerFactory.getLogger(ParityCheckWorker.class);
    private static final boolean shouldForceEmission = true;
    private final ChargeDao chargeDao;
    private ChargeService chargeService;

    private EmittedEventDao emittedEventDao;
    private StateTransitionService stateTransitionService;
    private EventService eventService;
    private RefundDao refundDao;
    private ParityCheckService parityCheckService;
    private HistoricalEventEmitter historicalEventEmitter;
    private long maxId;

    @Inject
    public ParityCheckWorker(ChargeDao chargeDao, ChargeService chargeService, LedgerService ledgerService, EmittedEventDao emittedEventDao,
                             StateTransitionService stateTransitionService, EventService eventService, RefundDao refundDao,
                             ParityCheckService parityCheckService) {
        this.chargeDao = chargeDao;
        this.chargeService = chargeService;
        this.emittedEventDao = emittedEventDao;
        this.stateTransitionService = stateTransitionService;
        this.eventService = eventService;
        this.refundDao = refundDao;
        this.parityCheckService = parityCheckService;
    }

    public void execute(Long startId, Optional<Long> maybeMaxId, boolean doNotReprocessValidRecords,
                        Optional<String> parityCheckStatus, Long doNotRetryEmitUntilDuration) {
        try {
            initializeHistoricalEventEmitter(doNotRetryEmitUntilDuration);

            MDC.put(HEADER_REQUEST_ID, "ParityCheckWorker-" + RandomUtils.nextLong(0, 10000));

            if (parityCheckStatus.isPresent()) {
                checkParityForParityCheckStatus(parityCheckStatus);
            } else {
                maxId = maybeMaxId.orElseGet(chargeDao::findMaxId);
                checkParityForIdRange(startId, maxId, doNotReprocessValidRecords);
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
        this.historicalEventEmitter = new HistoricalEventEmitter(emittedEventDao, refundDao, chargeService, shouldForceEmission,
                eventService, stateTransitionService, doNotRetryEmitUntilDuration);
    }

    private void checkParityForParityCheckStatus(Optional<String> parityCheckStatus) {
        ParityCheckStatus parityStatus = ParityCheckStatus.valueOf(parityCheckStatus.get());
        Long lastProcessedId = 0L;

        logger.info("Starting for status {}", parityCheckStatus.get());
        while (true) {
            List<ChargeEntity> charges = chargeDao.findByParityCheckStatus(parityStatus, PAGE_SIZE, lastProcessedId);

            if (!charges.isEmpty()) {
                logger.info("Processing charges [last processed id {}, no.of.charges {}] by parity check status", lastProcessedId, charges.size());
                charges.forEach(c -> checkParityFor(c, false));
                lastProcessedId = charges.get(charges.size() - 1).getId();
            } else {
                break;
            }
        }
    }

    public void checkParityForIdRange(long startId, long maxId, boolean doNotReprocessValidRecords) {
        logger.info("Starting from {} up to {}", startId, this.maxId);
        for (long i = startId; i <= this.maxId; i++) {
            final Optional<ChargeEntity> maybeCharge = chargeDao.findById(i);

            if (maybeCharge.isPresent()) {
                checkParityFor(maybeCharge.get(), doNotReprocessValidRecords);
            } else {
                logger.info("[{}/{}] - not found", i, this.maxId);
            }
        }
    }

    // needs to be public for transactional annotation
    @Transactional
    public void checkParityFor(ChargeEntity charge, boolean doNotReprocessValidRecords) {
        try {
            MDC.put("chargeId", charge.getExternalId());

            if (doNotReprocessValidRecords && ParityCheckStatus.EXISTS_IN_LEDGER.equals(charge.getParityCheckStatus())) {
                logger.info("transaction parity check skipped [id={},status={}]", charge.getId(), charge.getParityCheckStatus());
                return;
            }

            ParityCheckStatus parityCheckStatus = parityCheckService.getChargeAndRefundsParityCheckStatus(charge);
            chargeService.updateChargeParityStatus(charge.getExternalId(), parityCheckStatus);
            logger.info("transaction parity check finished [id={},status={}]", charge.getId(), parityCheckStatus);

            if (!parityCheckStatus.equals(ParityCheckStatus.EXISTS_IN_LEDGER)) {
                emitHistoricalEvents(charge);
            }
        } finally {
            MDC.remove("chargeId");
        }
    }

    private void emitHistoricalEvents(ChargeEntity charge) {
        historicalEventEmitter.processPaymentEvents(charge, true);
        historicalEventEmitter.processRefundEvents(charge.getExternalId());
    }
}
