//package uk.gov.pay.connector.service;
//
//import com.google.inject.persist.Transactional;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import uk.gov.pay.connector.dao.RefundTransactionDao;
//import uk.gov.pay.connector.model.domain.RefundStatus;
//
//import javax.inject.Inject;
//import java.util.Optional;
//
//import static java.lang.String.format;
//
//@Transactional
//public class RefundStatusUpdater {
//    private static final Logger logger = LoggerFactory.getLogger(RefundStatusUpdater.class);
//
//    private final RefundTransactionDao refundTransactionDao;
//
//    @Inject
//    public RefundStatusUpdater(RefundTransactionDao refundTransactionDao) {
//        this.refundTransactionDao = refundTransactionDao;
//    }
//
//    public void updateRefundTransactionStatus(PaymentGatewayName provider, String refundReference, RefundStatus newRefundStatus) {
//        Optional<RefundTransactionEntity> refundTransaction = refundTransactionDao.findByProviderAndReference(provider, refundReference);
//        if (refundTransaction.isPresent()) {
//            updateRefundTransactionStatus(newRefundStatus, refundTransaction.get());
//        } else {
//            logger.warn(format("Not updating refund transaction status for refundReference [%s] to [%s] refund transaction not found",
//                    refundReference,
//                    newRefundStatus.getValue()
//            ));
//        }
//    }
//
//    public void setReferenceAndUpdateTransactionStatus(String refundExternalId, String refundReference, RefundStatus newRefundStatus) {
//        Optional<RefundTransactionEntity> refundTransactionOptional = refundTransactionDao.findByExternalId(refundExternalId);
//        if (refundTransactionOptional.isPresent()) {
//            RefundTransactionEntity refundTransaction = refundTransactionOptional.get();
//            refundTransaction.setRefundReference(refundReference);
//            updateRefundTransactionStatus(newRefundStatus, refundTransaction);
//        } else {
//            logger.warn(format("Not updating refund transaction status for externalId [%s] to [%s] refund transaction not found",
//                        refundExternalId,
//                        newRefundStatus.getValue()
//                ));
//        }
//    }
//
//    private void updateRefundTransactionStatus(RefundStatus newRefundStatus, RefundTransactionEntity refundTransaction) {
//        logger.info("Changing refund transaction status for refundReference [{}] transactionId [{}] [{}]->[{}]",
//                refundTransaction.getRefundReference(),
//                refundTransaction.getRefundExternalId(),
//                refundTransaction.getStatus().getValue(),
//                newRefundStatus.getValue()
//        );
//
//        refundTransaction.updateStatus(newRefundStatus);
//    }
//}
