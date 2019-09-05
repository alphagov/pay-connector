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
import uk.gov.pay.connector.events.EventQueue;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.paritycheck.LedgerService;
import uk.gov.pay.connector.paritycheck.LedgerTransaction;
import uk.gov.pay.connector.queue.StateTransitionQueue;
import uk.gov.pay.connector.refund.dao.RefundDao;

import javax.inject.Inject;
import java.util.Optional;
import java.util.OptionalLong;

import static uk.gov.pay.connector.filters.RestClientLoggingFilter.HEADER_REQUEST_ID;

public class ParityCheckWorker {
    private static final Logger logger = LoggerFactory.getLogger(ParityCheckWorker.class);
    private final ChargeDao chargeDao;
    private ChargeService chargeService;
    private LedgerService ledgerService;
    private HistoricalEventEmitter historicalEventEmitter;
    private static final boolean shouldForceEmission = true;

    private long maxId;

    @Inject
    public ParityCheckWorker(ChargeDao chargeDao, ChargeService chargeService, LedgerService ledgerService, EmittedEventDao emittedEventDao,
                             StateTransitionQueue stateTransitionQueue, EventQueue eventQueue, RefundDao refundDao) {
        this.chargeDao = chargeDao;
        this.chargeService = chargeService;
        this.ledgerService = ledgerService;
        this.historicalEventEmitter = new HistoricalEventEmitter(emittedEventDao, stateTransitionQueue, eventQueue,
                refundDao, shouldForceEmission);
    }

    public void execute(Long startId, OptionalLong maybeMaxId) {
        try {
            MDC.put(HEADER_REQUEST_ID, "ParityCheckWorker-" + RandomUtils.nextLong(0, 10000));

            maxId = maybeMaxId.orElseGet(() -> chargeDao.findMaxId());
            logger.info("Starting from {} up to {}", startId, maxId);
            for (long i = startId; i <= maxId; i++) {
                checkParityFor(i);
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

    // needs to be public for transactional annotation
    @Transactional
    public void checkParityFor(long currentId) {
        final Optional<ChargeEntity> maybeCharge = chargeDao.findById(currentId);

        try {
            maybeCharge.ifPresent(c -> MDC.put("chargeId", c.getExternalId()));

            if (maybeCharge.isPresent()) {
                final ChargeEntity charge = maybeCharge.get();
                Optional<LedgerTransaction> transaction = ledgerService.getTransaction(charge.getExternalId());

                if (existsInLedger(transaction)) {
                    logger.info("transaction exists in ledger [id={}]", currentId);
                    chargeService.updateChargeParityStatus(charge.getExternalId(), ParityCheckStatus.EXISTS_IN_LEDGER);
                } else {
                    logger.info("transaction does not exist in ledger [id={}] -", currentId);
                    chargeService.updateChargeParityStatus(charge.getExternalId(), ParityCheckStatus.MISSING_IN_LEDGER);
                    emitHistoricalEvents(charge);
                }

            } else {
                logger.info("[{}/{}] - not found", currentId, maxId);
            }
        } finally {
            MDC.remove("chargeId");
        }
    }

    private boolean existsInLedger(Optional<LedgerTransaction> transaction) {
        return transaction.isPresent();
    }

    private void emitHistoricalEvents(ChargeEntity charge) {
        historicalEventEmitter.processPaymentEvents(charge);
        historicalEventEmitter.processRefundEvents(charge);
    }
}
