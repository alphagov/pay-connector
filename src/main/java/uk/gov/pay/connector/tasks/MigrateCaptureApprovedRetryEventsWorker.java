package uk.gov.pay.connector.tasks;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.RandomUtils;
import org.jboss.logging.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEventEntity;

import javax.inject.Inject;

import static uk.gov.pay.connector.filters.LoggingFilter.HEADER_REQUEST_ID;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;

public class MigrateCaptureApprovedRetryEventsWorker {

    private final ChargeDao chargeDao;
    private final PaymentRequestDao paymentRequestDao;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public MigrateCaptureApprovedRetryEventsWorker(ChargeDao chargeDao,
                                                   PaymentRequestDao paymentRequestDao) {
        this.chargeDao = chargeDao;
        this.paymentRequestDao = paymentRequestDao;
    }

    /**
     * The entry point into the processing pipeline.
     * It will fetch the MAX(id) from {@link PaymentRequestEntity} and
     * for each smaller <code>id</code> it will fetch the entity and
     * process it
     */
    public void execute(Long startId) {
        MDC.put(HEADER_REQUEST_ID, "Backfill RETRY events " + RandomUtils.nextLong(0, 10000));
        Long maxId = paymentRequestDao.findMaxId();
        logger.info("Running migration worker from startId [" + startId + "] to maxId [" + maxId + "]");
        for (long paymentRequestId = startId; paymentRequestId <= maxId; paymentRequestId++) {
            int retries = 0;
            updatePaymentRequestWithRetry(paymentRequestId, retries);
        }
    }

    private void updatePaymentRequestWithRetry(long paymentRequestId, long retries) {
        try {
            updatePaymentRequest(paymentRequestId);
        } catch (Exception exc) {
            if (retries < 3) {
                logger.error("Problem migrating [" + paymentRequestId +"] " + exc.getMessage() + " retry count [" + retries + "]");
                updatePaymentRequestWithRetry(paymentRequestId, retries + 1);
            } else {
                throw exc;
            }
        }
    }

    //Needs to be public to put the transaction here
    @SuppressWarnings("WeakerAccess")
    @Transactional
    public void updatePaymentRequest(long paymentRequestId) {
        logger.info("Migrating payment request [" + paymentRequestId + "]");
        paymentRequestDao.findById(PaymentRequestEntity.class, paymentRequestId)
                .ifPresent(this::processPaymentRequest);
    }

    private void processPaymentRequest(PaymentRequestEntity paymentRequest) {
        //Should make this a lot quicker
        if (paymentRequest.getChargeTransaction().getTransactionEvents().stream().anyMatch(transactionEvent -> transactionEvent.getStatus().equals(CAPTURE_APPROVED_RETRY))) {
            chargeDao.findByExternalId(paymentRequest.getExternalId())
                    .ifPresent(chargeEntity -> processCharge(chargeEntity, paymentRequest));
        }
    }

    private void processCharge(ChargeEntity charge, PaymentRequestEntity paymentRequestEntity) {
        charge.getEvents()
                .forEach(chargeEventEntity -> processChargeEvent(chargeEventEntity, paymentRequestEntity));
    }

    private void processChargeEvent(ChargeEventEntity chargeEvent, PaymentRequestEntity paymentRequestEntity) {
        final ChargeTransactionEntity chargeTransaction = paymentRequestEntity.getChargeTransaction();

        if (chargeEvent.getStatus().equals(CAPTURE_APPROVED_RETRY))
            if (chargeTransaction.getTransactionEvents()
                    .stream()
                    .filter(transactionEvent -> transactionEvent.getStatus().equals(CAPTURE_APPROVED_RETRY))
                    .noneMatch(transactionEvent -> transactionEvent.getUpdated().equals(chargeEvent.getUpdated()))) {

            logger.info("Adding charge event for paymentRequest [" + paymentRequestEntity.getId() +
                    "] transaction [" + chargeTransaction.getId() + "] status [" + chargeEvent.getStatus() + "]");
            ChargeTransactionEventEntity chargeTransactionEventEntity = new ChargeTransactionEventEntity();
            chargeTransactionEventEntity.setStatus(chargeEvent.getStatus());
            chargeTransactionEventEntity.setUpdated(chargeEvent.getUpdated());
            chargeEvent.getGatewayEventDate().ifPresent(chargeTransactionEventEntity::setGatewayEventDate);
            chargeTransaction.getTransactionEvents().add(chargeTransactionEventEntity);
            chargeTransactionEventEntity.setTransaction(chargeTransaction);
        } else {
            logger.info("Not adding charge event for paymentRequest [" + paymentRequestEntity.getId() +
                    "] transaction [" + chargeTransaction.getId() + "] status [" + chargeEvent.getStatus() + "]");
        }

    }
}
