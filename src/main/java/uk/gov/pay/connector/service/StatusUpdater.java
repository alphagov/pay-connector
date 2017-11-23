package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.function.Consumer;

@Transactional
public class StatusUpdater {
    private static final Logger logger = LoggerFactory.getLogger(StatusUpdater.class);

    private final PaymentRequestDao paymentRequestDao;

    @Inject
    public StatusUpdater(PaymentRequestDao paymentRequestDao) {
        this.paymentRequestDao = paymentRequestDao;
    }

    public  void updateChargeTransactionStatus(String externalId, ChargeStatus newChargeStatus, ZonedDateTime gatewayEventTime) {
        updateChargeTransactionStatus(
                externalId, newChargeStatus,
                (chargeTransaction) -> chargeTransaction.updateStatus(newChargeStatus, gatewayEventTime)
        );
    }

    public  void updateChargeTransactionStatus(String externalId, ChargeStatus newChargeStatus) {
        updateChargeTransactionStatus(
                externalId, newChargeStatus,
                (chargeTransaction) -> chargeTransaction.updateStatus(newChargeStatus)
        );
    }

    private void updateChargeTransactionStatus(String externalId, ChargeStatus newChargeStatus, Consumer<ChargeTransactionEntity> updateStatusFunction) {
        paymentRequestDao.findByExternalId(externalId).ifPresent(paymentRequestEntity -> {
            if (paymentRequestEntity.hasChargeTransaction()) {
                ChargeTransactionEntity chargeTransaction = paymentRequestEntity.getChargeTransaction();
                logger.info(String.format("Changing transaction status for externalId [%s] transactionId [%s] [%s]->[%s]",
                        externalId,
                        chargeTransaction.getId(),
                        chargeTransaction.getStatus().getValue(),
                        newChargeStatus.getValue())
                );
                updateStatusFunction.accept(chargeTransaction);
            } else {
                logger.info(
                        String.format("Not updating transaction status for externalId [%s] to [%s] charge transaction not found",
                                externalId,
                                newChargeStatus.getValue()
                        )
                );
            }
        });
    }
}
