package uk.gov.pay.connector.tasks;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.PaymentRequestDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.RefundHistory;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEventEntity;
import uk.gov.pay.connector.model.domain.transaction.RefundTransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.RefundTransactionEventEntity;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Predicate;

/**
 * Used to add any missing {@link ChargeTransactionEventEntity} to a
 * {@link PaymentRequestEntity} from {@link ChargeEntity}
 *
 * This worker class will go through each PaymentRequestEntity and it
 * will load the matching ChargeEntity and it will add to the
 * PaymentRequestEntity any missing {@link uk.gov.pay.connector.model.domain.transaction.TransactionEntity}
 * by comparing each {@link ChargeEntity} and {@link uk.gov.pay.connector.model.domain.RefundEntity}
 * from the matching {@link ChargeEntity}
 *
 * Once all the missing events have been added then the {@link PaymentRequestEntity}
 * is persisted and another one is loaded
 *
 */
public class PaymentRequestWorker {

    private final ChargeDao chargeDao;
    private final PaymentRequestDao paymentRequestDao;
    private final RefundDao refundDao;

    @Inject
    public PaymentRequestWorker(ChargeDao chargeDao,
                                PaymentRequestDao paymentRequestDao,
                                RefundDao refundDao) {
        this.chargeDao = chargeDao;
        this.paymentRequestDao = paymentRequestDao;
        this.refundDao = refundDao;
    }

    /**
     * The entry point into the processing pipeline.
     * It will fetch the MAX(id) from {@link PaymentRequestEntity} and
     * for each smaller <code>id</code> it will fetch the entity and
     * process it
     */
    public void execute() {
        Long maxId = paymentRequestDao.findMaxId();
        for (long i = 1; i <= maxId; i++) {
            updatePaymentRequest(i);
        }
    }

    //Needs to be public to put the transaction here
    @Transactional
    public void updatePaymentRequest(long paymentRequestId) {
        paymentRequestDao.findById(PaymentRequestEntity.class, paymentRequestId)
                .ifPresent(this::processPaymentRequest);
    }

    private void processPaymentRequest(PaymentRequestEntity paymentRequest) {
        chargeDao.findByExternalId(paymentRequest.getExternalId())
                .ifPresent(chargeEntity -> processCharge(chargeEntity, paymentRequest));
    }

    private void processCharge(ChargeEntity charge, PaymentRequestEntity paymentRequestEntity) {
        charge.getEvents()
                .forEach(chargeEventEntity -> processChargeEvent(chargeEventEntity, paymentRequestEntity));

        List<RefundHistory> refunds = refundDao.searchHistoryByChargeId(charge.getId());
        paymentRequestEntity.getRefundTransactions().forEach(refundTransactionEntity ->
                refunds.stream().filter(filterForRefundTransaction(refundTransactionEntity))
                        .forEach(refundHistory -> processRefundEvent(refundHistory, refundTransactionEntity)));

        paymentRequestDao.merge(paymentRequestEntity);
    }

    private Predicate<RefundHistory> filterForRefundTransaction(RefundTransactionEntity refundTransactionEntity) {
        return refundHistory ->
                refundHistory.getReference().equals(refundTransactionEntity.getRefundReference());
    }

    private void processChargeEvent(ChargeEventEntity chargeEvent, PaymentRequestEntity paymentRequestEntity) {
        final ChargeTransactionEntity chargeTransaction = paymentRequestEntity.getChargeTransaction();
        if (chargeTransaction.getTransactionEvents()
                .stream()
                .noneMatch(transactionEvent -> transactionEvent.getStatus().equals(chargeEvent.getStatus()))) {
            ChargeTransactionEventEntity chargeTransactionEventEntity = new ChargeTransactionEventEntity();
            chargeTransactionEventEntity.setStatus(chargeEvent.getStatus());
            chargeTransactionEventEntity.setUpdated(chargeEvent.getUpdated());
            chargeEvent.getGatewayEventDate().ifPresent(chargeTransactionEventEntity::setGatewayEventDate);
            chargeTransaction.getTransactionEvents().add(chargeTransactionEventEntity);
            chargeTransactionEventEntity.setTransaction(chargeTransaction);
        }
    }

    private void processRefundEvent(RefundHistory refundHistory, RefundTransactionEntity refundTransactionEntity) {
        if (refundTransactionEntity.getTransactionEvents()
                .stream()
                .noneMatch(refundEvent -> refundEvent.getStatus().equals(refundHistory.getStatus()))) {
            RefundTransactionEventEntity refundTransactionEventEntity = new RefundTransactionEventEntity();
            refundTransactionEventEntity.setStatus(refundHistory.getStatus());
            refundTransactionEventEntity.setUpdated(refundHistory.getCreatedDate());
            refundTransactionEntity.getTransactionEvents().add(refundTransactionEventEntity);
            refundTransactionEventEntity.setTransaction(refundTransactionEntity);
        }
    }
}
