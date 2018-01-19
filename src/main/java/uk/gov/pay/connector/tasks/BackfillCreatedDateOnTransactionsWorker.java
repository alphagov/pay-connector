package uk.gov.pay.connector.tasks;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.RandomUtils;
import org.jboss.logging.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.transaction.RefundTransactionEntity;

import javax.inject.Inject;

import java.util.Optional;

import static uk.gov.pay.connector.filters.LoggingFilter.HEADER_REQUEST_ID;

public class BackfillCreatedDateOnTransactionsWorker {
    private final ChargeDao chargeDao;
    private final PaymentRequestDao paymentRequestDao;
    private final RefundDao refundDao;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public BackfillCreatedDateOnTransactionsWorker(ChargeDao chargeDao,
                                                   PaymentRequestDao paymentRequestDao,
                                                   RefundDao refundDao) {
        this.chargeDao = chargeDao;
        this.paymentRequestDao = paymentRequestDao;
        this.refundDao = refundDao;
    }

    public void execute(Long startId) {
        MDC.put(HEADER_REQUEST_ID, "Back fill created_date on transactions " + RandomUtils.nextLong(0, 10000));
        logger.info("Running migration worker");
        Long maxId = paymentRequestDao.findMaxId();
        for (long paymentRequestId = startId; paymentRequestId <= maxId; paymentRequestId++) {
            int retries = 0;
            updateTransactionsWithRetry(paymentRequestId, retries);
        }
    }

    private void updateTransactionsWithRetry(long paymentRequestId, long retries) {
        try {
            setCreatedDateOnTransactions(paymentRequestId);
        } catch (Exception exc) {
            if (retries < 3) {
                logger.error("Problem migrating [" + paymentRequestId + "] " + " retry count [" + retries + "]", exc );
                updateTransactionsWithRetry(paymentRequestId, retries + 1);
            } else {
                throw exc;
            }
        }
    }

    @Transactional
    public void setCreatedDateOnTransactions(long paymentRequestId) {
        logger.info("Backfilling transactions for payment request [" + paymentRequestId + "]");

        paymentRequestDao.findById(PaymentRequestEntity.class, paymentRequestId)
                .ifPresent(paymentRequestEntity -> {
                    final Optional<ChargeEntity> chargeEntity = chargeDao.findByExternalId(paymentRequestEntity.getExternalId());
                    chargeEntity.ifPresent(charge ->
                            paymentRequestEntity.getChargeTransaction().setCreatedDate(charge.getCreatedDate())
                    );
                    paymentRequestEntity.getRefundTransactions().forEach(
                            this::updateRefundTransactionEntity);
                });
    }

    private void updateRefundTransactionEntity(RefundTransactionEntity refundTransactionEntity) {
        final String refundExternalId = refundTransactionEntity.getRefundExternalId();
        refundDao.findByExternalId(refundExternalId).ifPresent(
                refundEntity -> refundTransactionEntity.setCreatedDate(refundEntity.getCreatedDate()));
    }
}
