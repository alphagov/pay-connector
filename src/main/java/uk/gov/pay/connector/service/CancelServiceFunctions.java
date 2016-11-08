package uk.gov.pay.connector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.CardService.OperationType;
import uk.gov.pay.connector.service.transaction.NonTransactionalOperation;
import uk.gov.pay.connector.service.transaction.PreTransactionalOperation;
import uk.gov.pay.connector.service.transaction.TransactionContext;
import uk.gov.pay.connector.service.transaction.TransactionalOperation;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.gov.pay.connector.model.domain.ChargeStatus.fromString;

/**
 * Bunch of reusable functions and possibly sharable between Cancel/Expire
 */
class CancelServiceFunctions {

    private static final Logger logger = LoggerFactory.getLogger(CancelServiceFunctions.class);

    static TransactionalOperation<TransactionContext, ChargeEntity> changeStatusTo(ChargeDao chargeDao, ChargeEntity chargeEntity, ChargeStatus targetStatus) {
        return context -> {
            logger.info("Charge status to update - charge_external_id={}, status={}, to_status={}",
                    chargeEntity.getExternalId(), chargeEntity.getStatus(), targetStatus);
            chargeEntity.setStatus(targetStatus);
            return chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
        };
    }

    static PreTransactionalOperation<TransactionContext, ChargeEntity> prepareForTerminate(ChargeDao chargeDao,
                                                                                           ChargeEntity chargeEntity,
                                                                                           StatusFlow statusFlow,
                                                                                           ChargeCardDetailsService chargeCardDetailsService) {
        return context -> {
            ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);

            if (!reloadedCharge.hasStatus(statusFlow.getTerminatableStatuses())) {
                if (reloadedCharge.hasStatus(statusFlow.getLockState())) {
                    throw new OperationAlreadyInProgressRuntimeException(statusFlow.getName(), reloadedCharge.getExternalId());
                } else if (reloadedCharge.hasStatus(ChargeStatus.AUTHORISATION_READY)) {
                    throw new ConflictRuntimeException(chargeEntity.getExternalId());
                }

                logger.error("Charge is not in one of the legal states. charge_external_id={}, status={}, legal_states={}",
                        reloadedCharge.getExternalId(), reloadedCharge.getStatus(), getLegalStatusNames(statusFlow.getTerminatableStatuses()));

                throw new IllegalStateRuntimeException(reloadedCharge.getExternalId());
            }
            reloadedCharge.setStatus(statusFlow.getLockState());
            GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();

            logger.info("Card cancel request sent - charge_external_id={}, charge_status={}, account_id={}, transaction_id={}, amount={}, operation_type={}, provider={}, provider_type={}, locking_status={}",
                    chargeEntity.getExternalId(),
                    fromString(chargeEntity.getStatus()),
                    gatewayAccount.getId(),
                    chargeEntity.getGatewayTransactionId(),
                    chargeEntity.getAmount(),
                    OperationType.CANCELLATION.getValue(),
                    gatewayAccount.getGatewayName(),
                    gatewayAccount.getType(),
                    statusFlow.getLockState());

            return chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge);
        };
    }

    static NonTransactionalOperation<TransactionContext, GatewayResponse> doGatewayCancel(PaymentProviders providers) {
        return context -> {
            ChargeEntity chargeEntity = context.get(ChargeEntity.class);
            return providers.byName(chargeEntity.getPaymentGatewayName())
                    .cancel(CancelGatewayRequest.valueOf(chargeEntity));
        };
    }

    static String getLegalStatusNames(ChargeStatus[] legalStatuses) {
        return Stream.of(legalStatuses).map(ChargeStatus::toString).collect(Collectors.joining(", "));
    }
}
