package uk.gov.pay.connector.charge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.transaction.NonTransactionalOperation;
import uk.gov.pay.connector.charge.service.transaction.PreTransactionalOperation;
import uk.gov.pay.connector.charge.service.transaction.TransactionContext;
import uk.gov.pay.connector.charge.service.transaction.TransactionalOperation;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.fromString;

/**
 * Bunch of reusable functions and possibly sharable between Cancel/Expire
 */
class CancelServiceFunctions {

    private static final Logger logger = LoggerFactory.getLogger(CancelServiceFunctions.class);

    private CancelServiceFunctions() {
        // prevent people to instantiate this class, as it has only static methods
    }

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
            if (!chargeIsInTerminatableStatus(statusFlow, chargeEntity)) {
                if (newStatus.equals(ChargeStatus.fromString(chargeEntity.getStatus()))) {
                    throw new OperationAlreadyInProgressRuntimeException(statusFlow.getName(), chargeId);
                } else if (Arrays.asList(AUTHORISATION_READY, AUTHORISATION_3DS_READY).contains(ChargeStatus.fromString(chargeEntity.getStatus()))) {
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

    private static String getLegalStatusNames(List<ChargeStatus> legalStatuses) {
        return legalStatuses.stream().map(ChargeStatus::toString).collect(Collectors.joining(", "));
    }

    private static boolean chargeIsInTerminatableStatus(StatusFlow statusFlow, ChargeEntity chargeEntity) {
        return statusFlow.getTerminatableStatuses().contains(ChargeStatus.fromString(chargeEntity.getStatus()));
    }

}
