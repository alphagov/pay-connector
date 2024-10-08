package uk.gov.pay.connector.tasks.service;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.DATA_MISMATCH;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.EXISTS_IN_LEDGER;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.MISSING_IN_LEDGER;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.CREATED;
import static uk.gov.pay.connector.tasks.service.ParityCheckService.FIELD_NAME;
import static uk.gov.service.payments.commons.model.CommonDateTimeFormatters.ISO_INSTANT_MILLISECOND_PRECISION;
import static uk.gov.service.payments.logging.LoggingKeys.REFUND_EXTERNAL_ID;

public class RefundParityChecker {

    private static final Logger logger = LoggerFactory.getLogger(RefundParityChecker.class);
    private final RefundDao refundDao;

    @Inject
    public RefundParityChecker(RefundDao refundDao) {
        this.refundDao = refundDao;
    }

    public ParityCheckStatus checkParity(RefundEntity refundEntity, LedgerTransaction transaction) {
        String externalId = refundEntity.getExternalId();
        ParityCheckStatus parityCheckStatus;

        MDC.put(REFUND_EXTERNAL_ID, externalId);

        if (transaction == null) {
            logger.info("Refund transaction missing in Ledger for Refund [external_id={}]", externalId);
            parityCheckStatus = MISSING_IN_LEDGER;
        } else {
            boolean fieldsMatch;

            fieldsMatch = isEquals(externalId, transaction.getTransactionId(), "external_id");

            if (isBlank(transaction.getGatewayAccountId())) {
                logger.info("Field value does not match between ledger and connector [field_name={}]", "gateway_account_id",
                        kv(FIELD_NAME, "gateway_account_id"));
                fieldsMatch = false;
            }

            fieldsMatch = fieldsMatch && isEquals(refundEntity.getChargeExternalId(), transaction.getParentTransactionId(), "parent_transaction_id");
            fieldsMatch = fieldsMatch && isEquals(refundEntity.getAmount(), transaction.getAmount(), "amount");
            fieldsMatch = fieldsMatch && isEquals(refundEntity.getGatewayTransactionId(), transaction.getGatewayTransactionId(), "gateway_transaction_id");
            fieldsMatch = fieldsMatch && isEquals(refundEntity.getUserExternalId(), transaction.getRefundedBy(), "refunded_by");
            fieldsMatch = fieldsMatch && isEquals(refundEntity.getUserEmail(), transaction.getRefundedByUserEmail(), "refunded_by_user_email");

            String refundCreatedEventDate = getRefundCreatedDate(refundEntity.getExternalId())
                    .map(ISO_INSTANT_MILLISECOND_PRECISION::format).orElse(null);
            fieldsMatch = fieldsMatch && isEquals(refundCreatedEventDate, transaction.getCreatedDate(), "created_date");

            String refundExternalStatus = refundEntity.getStatus().toExternal().getStatus();
            fieldsMatch = fieldsMatch && isEquals(refundExternalStatus,
                    transaction.getState() != null ? transaction.getState().getStatus() : null,
                    "status");

            if (fieldsMatch) {
                parityCheckStatus = EXISTS_IN_LEDGER;
            } else {
                parityCheckStatus = DATA_MISMATCH;
            }
        }
        MDC.remove(REFUND_EXTERNAL_ID);
        return parityCheckStatus;
    }

    private Optional<ZonedDateTime> getRefundCreatedDate(String refundExternalId) {
        Optional<RefundHistory> refundHistoryForCreatedEvent =
                refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(refundExternalId, CREATED);

        return refundHistoryForCreatedEvent.map(RefundHistory::getHistoryStartDate);
    }

    private boolean isEquals(Object value1, Object value2, String fieldName) {
        if (Objects.equals(value1, value2)) {
            return true;
        } else {
            logger.info("Field value does not match between ledger and connector [field_name={}]", fieldName,
                    kv(FIELD_NAME, fieldName));
            return false;
        }
    }
}
