package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;

@Transactional
public class StatusUpdater {

    private final PaymentRequestDao paymentRequestDao;

    @Inject
    public StatusUpdater(PaymentRequestDao paymentRequestDao) {
        this.paymentRequestDao = paymentRequestDao;
    }

    public  void updateChargeTransactionStatus(String externalId, ChargeStatus newChargeStatus) {
        paymentRequestDao.findByExternalId(externalId).ifPresent(paymentRequestEntity -> {
            if (paymentRequestEntity.hasChargeTransaction()) {
                paymentRequestEntity.getChargeTransaction().setStatus(newChargeStatus);
            }
        });
    }
}
