package uk.gov.pay.connector.tasks.service;

import com.google.inject.persist.Transactional;
import jakarta.inject.Inject;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.tasks.HistoricalEventEmitter;

import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.EXISTS_IN_LEDGER;
import static uk.gov.service.payments.logging.LoggingKeys.MDC_REQUEST_ID_KEY;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.REFUND_EXTERNAL_ID;

public class ParityCheckerService {
    private static final int PAGE_SIZE = 100;
    private static final Logger logger = LoggerFactory.getLogger(ParityCheckerService.class);
    private final ChargeDao chargeDao;
    private final ChargeService chargeService;

    private final EmittedEventDao emittedEventDao;
    private final StateTransitionService stateTransitionService;
    private final EventService eventService;
    private final RefundService refundService;
    private final RefundDao refundDao;
    private final ParityCheckService parityCheckService;
    private HistoricalEventEmitter historicalEventEmitter;

    @Inject
    public ParityCheckerService(ChargeDao chargeDao, ChargeService chargeService, EmittedEventDao emittedEventDao, StateTransitionService stateTransitionService, EventService eventService, RefundService refundService, RefundDao refundDao, ParityCheckService parityCheckService) {
        this.chargeDao = chargeDao;
        this.chargeService = chargeService;
        this.emittedEventDao = emittedEventDao;
        this.stateTransitionService = stateTransitionService;
        this.eventService = eventService;
        this.refundService = refundService;
        this.refundDao = refundDao;
        this.parityCheckService = parityCheckService;
    }

    public void checkParity(Long startId, Optional<Long> maybeMaxId, boolean doNotReprocessValidRecords, Optional<String> parityCheckStatus, Long doNotRetryEmitUntilDuration) {
        Long maxId = maybeMaxId.orElseGet(chargeDao::findMaxId);
        try {
            initializeHistoricalEventEmitter(doNotRetryEmitUntilDuration);

            MDC.put(MDC_REQUEST_ID_KEY, "ParityCheckWorker-" + RandomUtils.secure().randomLong(0, 10000));

            if (parityCheckStatus.isPresent()) {
                checkParityForParityCheckStatus(parityCheckStatus.get());
            } else {
                checkParityForIdRange(startId, maxId, doNotReprocessValidRecords);
            }
        } catch (NullPointerException e) {
            for (StackTraceElement s : e.getStackTrace()) {
                logger.error("Null pointer exception stack trace: {}", s);
            }
            logger.error("Null pointer exception [start={}] [max={}] [error={}]", startId, maxId, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error attempting to process payment events on job [start={}] [max={}] [error={}]", startId, maxId, e.getMessage(), e);
        }

        logger.info("Terminating");
        MDC.remove(MDC_REQUEST_ID_KEY);
    }

    public void checkParityForRefundsOnly(Long startId, Long maxId, boolean doNotReprocessValidRecords, String parityCheckStatus, Long doNotRetryEmitUntilDuration) {
        String parityCheckRequestId = "ParityCheckWorker-" + RandomUtils.secure().randomLong(0, 10000);
        try {
            initializeHistoricalEventEmitter(doNotRetryEmitUntilDuration);
            MDC.put(MDC_REQUEST_ID_KEY, parityCheckRequestId);

            if (isNotBlank(parityCheckStatus)) {
                processRefundsByParityCheckStatus(parityCheckStatus, doNotReprocessValidRecords);
            } else {
                maxId = ofNullable(maxId).orElseGet(refundDao::findMaxId);
                processRefundsByIdRange(startId, maxId, doNotReprocessValidRecords);
            }
        } catch (Exception e) {
            logger.error("Error parity checking refunds on job [start={}] [max={}] [error={}]", startId, maxId, e.getMessage(), e);
        }
        logger.info("Terminating");
        MDC.remove(MDC_REQUEST_ID_KEY);
    }

    private void initializeHistoricalEventEmitter(Long doNotRetryEmitUntilDuration) {
        this.historicalEventEmitter = new HistoricalEventEmitter(emittedEventDao, refundDao, chargeService, eventService, stateTransitionService, doNotRetryEmitUntilDuration);
    }

    private void checkParityForParityCheckStatus(String parityCheckStatus) {
        ParityCheckStatus parityStatus = ParityCheckStatus.valueOf(parityCheckStatus);
        Long lastProcessedId = 0L;

        logger.info("Starting for status {}", parityCheckStatus);
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
        logger.info("Starting from {} up to {}", startId, maxId);
        for (long i = startId; i <= maxId; i++) {
            final Optional<ChargeEntity> maybeCharge = chargeDao.findById(i);

            if (maybeCharge.isPresent()) {
                checkParityFor(maybeCharge.get(), doNotReprocessValidRecords);
            } else {
                logger.info("[{}/{}] - not found", i, maxId);
            }
        }
    }

    // needs to be public for transactional annotation
    @Transactional
    public void checkParityFor(ChargeEntity charge, boolean doNotReprocessValidRecords) {
        try {
            MDC.put(PAYMENT_EXTERNAL_ID, charge.getExternalId());

            if (skipParityCheck(charge.getId(), charge.getParityCheckStatus(), doNotReprocessValidRecords)) {
                return;
            }

            ParityCheckStatus parityCheckStatus = parityCheckService.getChargeAndRefundsParityCheckStatus(charge);
            chargeService.updateChargeParityStatus(charge.getExternalId(), parityCheckStatus);
            logger.info("transaction parity check finished [id={},status={}]", charge.getId(), parityCheckStatus);

            if (!parityCheckStatus.equals(EXISTS_IN_LEDGER)) {
                historicalEventEmitter.processPaymentEvents(charge, true);
            }
        } finally {
            MDC.remove("chargeId");
        }
    }

    @Transactional
    public void checkParityForRefund(RefundEntity refund, boolean doNotReprocessValidRecords) {
        try {
            MDC.put(REFUND_EXTERNAL_ID, refund.getExternalId());

            if (skipParityCheck(refund.getId(), refund.getParityCheckStatus(), doNotReprocessValidRecords)) {
                return;
            }

            ParityCheckStatus parityCheckStatus = parityCheckService.getRefundParityCheckStatus(refund);
            refundService.updateRefundParityStatus(refund.getExternalId(), parityCheckStatus);
            logger.info("refund transaction parity check finished [id={},status={}]", refund.getId(), parityCheckStatus);

            if (!parityCheckStatus.equals(EXISTS_IN_LEDGER)) {
                historicalEventEmitter.emitEventsForRefund(refund.getExternalId(), true);
            }
        } finally {
            MDC.remove(REFUND_EXTERNAL_ID);
        }
    }

    private void processRefundsByIdRange(long startId, long maxId, boolean doNotReprocessValidRecords) {
        logger.info("Starting parity check for refunds for IDs from {} up to {}", startId, maxId);
        for (long refundId = startId; refundId <= maxId; refundId++) {
            Optional<RefundEntity> mayBeRefund = refundDao.findById(refundId);

            if (mayBeRefund.isPresent()) {
                checkParityForRefund(mayBeRefund.get(), doNotReprocessValidRecords);
            } else {
                logger.info("[{}/{}] - not found", refundId, maxId);
            }
        }
    }

    private void processRefundsByParityCheckStatus(String parityCheckStatus, boolean doNotReprocessValidRecords) {
        ParityCheckStatus parityStatus = ParityCheckStatus.valueOf(parityCheckStatus);
        Long lastProcessedId = 0L;

        logger.info("Starting for status {}", parityCheckStatus);
        while (true) {
            List<RefundEntity> refunds = refundDao.findByParityCheckStatus(parityStatus, PAGE_SIZE, lastProcessedId);

            if (!refunds.isEmpty()) {
                logger.info("Processing refunds [last processed id {}, no.of.refunds {}] by parity check status", lastProcessedId, refunds.size());
                refunds.forEach(refund -> checkParityForRefund(refund, doNotReprocessValidRecords));
                lastProcessedId = refunds.get(refunds.size() - 1).getId();
            } else {
                break;
            }
        }
    }

    private boolean skipParityCheck(Long id, ParityCheckStatus parityCheckStatus, boolean doNotReprocessValidRecords) {
        if (doNotReprocessValidRecords && EXISTS_IN_LEDGER.equals(parityCheckStatus)) {
            logger.info("transaction parity check skipped [id={},status={}]", id, parityCheckStatus);
            return true;
        }
        return false;
    }
}
