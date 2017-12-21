package uk.gov.pay.connector.tasks;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.RandomUtils;
import org.jboss.logging.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.CardDao;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.domain.CardEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;

import javax.inject.Inject;

import static uk.gov.pay.connector.filters.LoggingFilter.HEADER_REQUEST_ID;

public class AddTransactionIdToCardsWorker {
    private final ChargeDao chargeDao;
    private final PaymentRequestDao paymentRequestDao;
    private final CardDao cardDao;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public AddTransactionIdToCardsWorker(ChargeDao chargeDao, PaymentRequestDao paymentRequestDao, CardDao cardDao) {
        this.chargeDao = chargeDao;
        this.paymentRequestDao = paymentRequestDao;
        this.cardDao = cardDao;
    }

    public void execute(Long startId) {
        MDC.put(HEADER_REQUEST_ID, "Back fill transaction id on cards " + RandomUtils.nextLong(0, 10000));
        logger.info("Running migration worker");
        Long maxId = cardDao.findMaxId();
        for (long cardId = startId; cardId <= maxId; cardId++) {
            int retries = 0;
            updateCardWithRetry(cardId, retries);
        }
    }

    private void updateCardWithRetry(long cardId, long retries) {
        try {
            setChargeTransactionOnCard(cardId);
        } catch (Exception exc) {
            if (retries < 3) {
                logger.error("Problem migrating [" + cardId + "] " + exc.getMessage() + " retry count [" + retries + "]");
                updateCardWithRetry(cardId, retries + 1);
            } else {
                throw exc;
            }
        }
    }

    @Transactional
    public void setChargeTransactionOnCard(long cardId) {
        logger.info("Migrating card [" + cardId + "]");

        cardDao.findById(CardEntity.class, cardId)
                .filter(card -> card.getChargeTransactionEntity() == null)
                .ifPresent(card ->
                        chargeDao.findById(card.getChargeId())
                                .ifPresent(charge -> loadPaymentRequestAndAddToCard(card, charge))
                );
    }

    private void loadPaymentRequestAndAddToCard(CardEntity card, ChargeEntity charge) {
        paymentRequestDao.findByExternalId(charge.getExternalId()).ifPresent(paymentRequest -> {
                    ChargeTransactionEntity chargeTransaction = paymentRequest.getChargeTransaction();
                    card.setChargeTransactionEntity(chargeTransaction);

                    logger.info("Adding charge transaction [" + chargeTransaction.getId() + "] to card [" + card.getId() + "]");
                }
        );
    }
}
