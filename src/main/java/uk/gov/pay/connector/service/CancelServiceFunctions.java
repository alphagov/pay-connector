package uk.gov.pay.connector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.fromString;

/**
 * Bunch of reusable functions and possibly sharable between Cancel/Expire
 */
class CancelServiceFunctions {

    private static final Logger logger = LoggerFactory.getLogger(CancelServiceFunctions.class);

    static TransactionalOperation<TransactionContext, ChargeEntity> changeStatusTo(ChargeDao chargeDao, ChargeEventDao chargeEventDao, String chargeId, ChargeStatus targetStatus, Optional<ZonedDateTime> generationTimeOptional) {
        return context -> chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> {
                    logger.info("Charge status to update - charge_external_id={}, status={}, to_status={}",
                            chargeEntity.getExternalId(), chargeEntity.getStatus(), targetStatus);
                    chargeEntity.setStatus(targetStatus);
                    chargeEventDao.persistChargeEventOf(chargeEntity, generationTimeOptional);
                    return chargeEntity;
                })
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    static PreTransactionalOperation<TransactionContext, ChargeEntity> prepareForTerminate(ChargeDao chargeDao,
                                                                                           ChargeEventDao chargeEventDao,
                                                                                           String chargeId,
                                                                                           StatusFlow statusFlow
                                                                                           ) {
        return context -> chargeDao.findByExternalId(chargeId).map(chargeEntity -> {
            ChargeStatus newStatus = statusFlow.getLockState();
            if (!chargeEntity.hasStatus(statusFlow.getTerminatableStatuses())) {
                if (chargeEntity.hasStatus(newStatus)) {
                    throw new OperationAlreadyInProgressRuntimeException(statusFlow.getName(), chargeId);
                } else if (chargeEntity.hasStatus(ChargeStatus.AUTHORISATION_READY, AUTHORISATION_3DS_READY)) {
                    throw new ConflictRuntimeException(chargeEntity.getExternalId());
                }

                logger.error("Charge is not in one of the legal states. charge_external_id={}, status={}, legal_states={}",
                        chargeId, chargeEntity.getStatus(), getLegalStatusNames(statusFlow.getTerminatableStatuses()));

                throw new IllegalStateRuntimeException(chargeId);
            }
            chargeEntity.setStatus(newStatus);
            
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
                    newStatus);

            chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());

            return chargeEntity;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    static NonTransactionalOperation<TransactionContext, GatewayResponse> doGatewayCancel(PaymentProviders providers) {
        return context -> {
            ChargeEntity chargeEntity = context.get(ChargeEntity.class);
            return providers.byName(chargeEntity.getPaymentGatewayName())
                    .cancel(CancelGatewayRequest.valueOf(chargeEntity));
        };
    }

    static String getLegalStatusNames(List<ChargeStatus> legalStatuses) {
        return legalStatuses.stream().map(ChargeStatus::toString).collect(Collectors.joining(", "));
    }
}
