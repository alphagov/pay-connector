package uk.gov.pay.connector.tasks;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.RandomUtils;
import org.jboss.logging.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.Card3dsDao;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.domain.Card3dsEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;

import javax.inject.Inject;

import static uk.gov.pay.connector.filters.LoggingFilter.HEADER_REQUEST_ID;

public class AddTransactionIdToCard3dsWorker {
    private final ChargeDao chargeDao;
    private final PaymentRequestDao paymentRequestDao;
    private final Card3dsDao card3dsDao;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public AddTransactionIdToCard3dsWorker(ChargeDao chargeDao, PaymentRequestDao paymentRequestDao, Card3dsDao card3dsDao) {
        this.chargeDao = chargeDao;
        this.paymentRequestDao = paymentRequestDao;
        this.card3dsDao = card3dsDao;
    }

    public void execute() {
        MDC.put(HEADER_REQUEST_ID, "Back fill transaction id on cards " + RandomUtils.nextLong(0, 10000));
        logger.info("Running migration worker");
        Long maxId = card3dsDao.findMaxId();
        for (long card3dsId = 1; card3dsId <= maxId; card3dsId++) {
            int retries = 0;
            updateCard3dsWithRetry(card3dsId, retries);
        }
    }

    private void updateCard3dsWithRetry(long cardId, long retries) {
        try {
            setChargeTransactionOnCard3ds(cardId);
        } catch (Exception exc) {
            if (retries < 3) {
                logger.error("Problem migrating [" + cardId + "] " + exc.getMessage() + " retry count [" + retries + "]");
                updateCard3dsWithRetry(cardId, retries + 1);
            } else {
                throw exc;
            }
        }
    }

    @Transactional
    public void setChargeTransactionOnCard3ds(long card3dsId) {
        logger.info("Migrating card3ds [" + card3dsId + "]");

        card3dsDao.findById(Card3dsEntity.class, card3dsId)
                .filter(card -> card.getChargeTransactionEntity() == null)
                .ifPresent(card3ds ->
                        chargeDao.findById(card3ds.getChargeId())
                                .ifPresent(charge -> loadPaymentRequestAndAddToCard(card3ds, charge))
                );
    }

    private void loadPaymentRequestAndAddToCard(Card3dsEntity card3ds, ChargeEntity charge) {
        paymentRequestDao.findByExternalId(charge.getExternalId()).ifPresent(paymentRequest -> {
                    ChargeTransactionEntity chargeTransaction = paymentRequest.getChargeTransaction();
                    chargeTransaction.setCard3ds(card3ds);

                    logger.info("Adding charge transaction [" + chargeTransaction.getId() + "] to card3ds [" + card3ds.getId() + "]");
                }
        );
    }
}
