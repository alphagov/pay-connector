package uk.gov.pay.connector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.transaction.NonTransactionalOperation;
import uk.gov.pay.connector.service.transaction.PreTransactionalOperation;
import uk.gov.pay.connector.service.transaction.TransactionContext;
import uk.gov.pay.connector.service.transaction.TransactionalOperation;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.ChargeStatus.fromString;

/**
 * Bunch of reusable functions and possibly sharable between Cancel/Expire
 */
class CancelServiceFunctions {

    private static final Logger logger = LoggerFactory.getLogger(CancelServiceFunctions.class);

    static TransactionalOperation<TransactionContext, ChargeEntity> changeStatusTo(ChargeDao chargeDao, ChargeEntity chargeEntity, ChargeStatus targetStatus) {
        return context -> {
            logger.info("charge status to update - from: {}, to: {} for Charge ID: {}",
                    chargeEntity.getStatus(), targetStatus, chargeEntity.getId());
            chargeEntity.setStatus(targetStatus);
            return chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
        };
    }

    static PreTransactionalOperation<TransactionContext, ChargeEntity> prepareForTerminate(ChargeDao chargeDao,
                                                                                           ChargeEntity chargeEntity,
                                                                                           StatusFlow statusFlow,
                                                                                           ConfirmationDetailsService confirmationDetailsService) {
        return context -> {
            ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);

            if (!reloadedCharge.hasStatus(statusFlow.getTerminatableStatuses())) {
                if (reloadedCharge.hasStatus(statusFlow.getLockState())) {
                    throw new OperationAlreadyInProgressRuntimeException(statusFlow.getName(), reloadedCharge.getExternalId());
                }
                logger.error(format("Charge with id [%s] and with status [%s] should be in one of the following legal states, [%s]",
                        reloadedCharge.getId(), reloadedCharge.getStatus(), getLegalStatusNames(statusFlow.getTerminatableStatuses())));
                throw new IllegalStateRuntimeException(reloadedCharge.getExternalId());
            }
            reloadedCharge.setStatus(statusFlow.getLockState());

            logger.info(format("Card cancel request sent - charge_external_id=%s, transaction_id=%s, status=%s",
                    chargeEntity.getExternalId(), chargeEntity.getGatewayTransactionId(), fromString(chargeEntity.getStatus())));

            ChargeEntity reloadedEntity = chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge);
            confirmationDetailsService.doRemove(reloadedEntity);
            return reloadedEntity;
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
