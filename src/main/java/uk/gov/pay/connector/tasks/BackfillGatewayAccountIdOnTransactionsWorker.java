package uk.gov.pay.connector.tasks;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.RandomUtils;
import org.jboss.logging.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;

import javax.inject.Inject;

import static uk.gov.pay.connector.filters.LoggingFilter.HEADER_REQUEST_ID;

public class BackfillGatewayAccountIdOnTransactionsWorker {
    private final ChargeDao chargeDao;
    private final PaymentRequestDao paymentRequestDao;
    private final RefundDao refundDao;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public BackfillGatewayAccountIdOnTransactionsWorker(ChargeDao chargeDao,
                                                        PaymentRequestDao paymentRequestDao,
                                                        RefundDao refundDao) {
        this.chargeDao = chargeDao;
        this.paymentRequestDao = paymentRequestDao;
        this.refundDao = refundDao;
    }

    public void execute(Long startId) {
        MDC.put(HEADER_REQUEST_ID, "Back fill gateway_account_id on transactions " + RandomUtils.nextLong(0, 10000));
        logger.info("Running migration worker");
        Long maxId = paymentRequestDao.findMaxId();
        for (long paymentRequestId = startId; paymentRequestId <= maxId; paymentRequestId++) {
            int retries = 0;
            updateTransactionsWithRetry(paymentRequestId, retries);
        }
    }

    private void updateTransactionsWithRetry(long paymentRequestId, long retries) {
        try {
            setGatewayAccountOnTransactions(paymentRequestId);
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
    public void setGatewayAccountOnTransactions(long paymentRequestId) {
        logger.info("Backfilling transactions for payment request [" + paymentRequestId + "]");

        paymentRequestDao.findById(PaymentRequestEntity.class, paymentRequestId)
                .ifPresent(paymentRequestEntity -> {
                    Long gatewayAccountId = paymentRequestEntity.getGatewayAccount().getId();
                    paymentRequestEntity.getTransactions()
                            .forEach(transaction -> transaction.setGatewayAccountId(gatewayAccountId));
                });
    }
}
