package uk.gov.pay.connector.tasks;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.RandomUtils;
import org.jboss.logging.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;

import javax.inject.Inject;
import java.util.Optional;

import static uk.gov.pay.connector.filters.LoggingFilter.HEADER_REQUEST_ID;

public class MigrateEmailWorker {
    private final ChargeDao chargeDao;
    private final PaymentRequestDao paymentRequestDao;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public MigrateEmailWorker(ChargeDao chargeDao,
                              PaymentRequestDao paymentRequestDao) {
        this.chargeDao = chargeDao;
        this.paymentRequestDao = paymentRequestDao;
    }

    public void execute(Long startId) {
        MDC.put(HEADER_REQUEST_ID, "Back fill email on transactions " + RandomUtils.nextLong(0, 10000));
        logger.info("Running migration worker");
        Long maxId = paymentRequestDao.findMaxId();
        for (long paymentRequestId = startId; paymentRequestId <= maxId; paymentRequestId++) {
            int retries = 0;
            updateTransactionsWithRetry(paymentRequestId, retries);
        }
    }

    private void updateTransactionsWithRetry(long paymentRequestId, long retries) {
        try {
            setEmailOnChargeTransaction(paymentRequestId);
        } catch (Exception exc) {
            if (retries < 3) {
                logger.error("Problem migrating [" + paymentRequestId + "] " + " retry count [" + retries + "]", exc );
                updateTransactionsWithRetry(paymentRequestId, retries + 1);
            } else {
                throw exc;
            }
        }
    }

    //Needs to be public to put the transaction here
    @SuppressWarnings("WeakerAccess")
    @Transactional
    public void setEmailOnChargeTransaction(long paymentRequestId) {
        logger.info("Back filling transactions for payment request [" + paymentRequestId + "]");

        paymentRequestDao.findById(PaymentRequestEntity.class, paymentRequestId)
                .ifPresent(paymentRequestEntity -> {
                    final Optional<ChargeEntity> chargeEntity = chargeDao.findByExternalId(paymentRequestEntity.getExternalId());
                    chargeEntity.ifPresent(charge ->
                            paymentRequestEntity.getChargeTransaction().setEmail(charge.getEmail())
                    );
                });
    }
}
