package uk.gov.pay.connector.service;

import com.google.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.CancelGatewayResponse;
import uk.gov.pay.connector.model.CancelRequest;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.transaction.NonTransactionalOperation;
import uk.gov.pay.connector.service.transaction.TransactionContext;
import uk.gov.pay.connector.service.transaction.TransactionFlow;
import uk.gov.pay.connector.service.transaction.TransactionalOperation;

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.CancelGatewayResponse.successfulCancelResponse;
import static uk.gov.pay.connector.model.ErrorResponse.baseError;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.FAILED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.service.ChargeCancelService.CancellationStatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.service.ChargeCancelService.CancellationStatusFlow.USER_CANCELLATION_FLOW;

public class ChargeCancelService {

    private static final String CANCELLATION = "Cancellation";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static ChargeStatus[] nonGatewayStatuses = new ChargeStatus[]{
            CREATED, ENTERING_CARD_DETAILS
    };

    private final ChargeDao chargeDao;
    private final PaymentProviders providers;
    private final Provider<TransactionFlow> transactionFlowProvider;


    @Inject
    public ChargeCancelService(ChargeDao chargeDao, PaymentProviders providers,
                               Provider<TransactionFlow> transactionFlowProvider) {
        this.chargeDao = chargeDao;
        this.providers = providers;
        this.transactionFlowProvider = transactionFlowProvider;
    }

    public Optional<GatewayResponse> doSystemCancel(String chargeId, Long accountId) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> doCancel(chargeEntity, SYSTEM_CANCELLATION_FLOW))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    public Optional<GatewayResponse> doUserCancel(String chargeId) {
        return chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> doCancel(chargeEntity, USER_CANCELLATION_FLOW))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private Optional<GatewayResponse> doCancel(ChargeEntity chargeEntity, CancellationStatusFlow statusFlow) {
        if (chargeEntity.hasStatus(nonGatewayStatuses)) {
            return Optional.of(nonGatewayCancel(chargeEntity, statusFlow));
        } else {
            return doGatewayCancel(chargeEntity, statusFlow);
        }
    }

    private Optional<GatewayResponse> doGatewayCancel(ChargeEntity charge, CancellationStatusFlow statusFlow) {
        return Optional.ofNullable(transactionFlowProvider.get()
                .executeNext(prepareForCancel(charge, statusFlow))
                .executeNext(doGatewayCancel())
                .executeNext(finishCancel(determineTerminalState(statusFlow.getSuccessTerminalState(), statusFlow.getFailureTerminalState())))
                .complete()
                .get(CancelGatewayResponse.class));

    }

    private GatewayResponse nonGatewayCancel(ChargeEntity chargeEntity, CancellationStatusFlow statusFlow) {
        ChargeStatus completeStatus = statusFlow.getSuccessTerminalState();
        ChargeEntity processedCharge = transactionFlowProvider.get()
                .executeNext((TransactionalOperation<TransactionContext, ChargeEntity>) context -> {
                    logger.info("charge status to update - from: " + chargeEntity.getStatus() + ", to: " + completeStatus + " for Charge ID: " + chargeEntity.getId());
                    chargeEntity.setStatus(completeStatus);
                    return chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
                }).complete()
                .get(ChargeEntity.class);

        if (completeStatus.getValue().equals(processedCharge.getStatus())) {
            return successfulCancelResponse(completeStatus);
        } else {
            String errorMsg = format("Could not update chargeId [%s] to status [%s]. Current state [%s]",
                    processedCharge.getExternalId(),
                    completeStatus,
                    processedCharge.getStatus());
            logger.error(errorMsg);
            return new CancelGatewayResponse(FAILED, baseError(errorMsg), statusFlow.getFailureTerminalState());
        }
    }

    private TransactionalOperation<TransactionContext, ChargeEntity> prepareForCancel(ChargeEntity chargeEntity, CancellationStatusFlow statusFlow) {

        return context -> {
            ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);

            if (!reloadedCharge.hasStatus(statusFlow.getCancellableStatuses())) {
                if (reloadedCharge.hasStatus(statusFlow.getLockState())) {
                    throw new OperationAlreadyInProgressRuntimeException(CANCELLATION, reloadedCharge.getExternalId());
                }
                logger.error(format("Charge with id [%s] and with status [%s] should be in one of the following legal states, [%s]",
                        reloadedCharge.getId(), reloadedCharge.getStatus(), getLegalStatusNames(statusFlow.getCancellableStatuses())));
                throw new IllegalStateRuntimeException(reloadedCharge.getExternalId());
            }

            logger.info("charge status to update - from: " + chargeEntity.getStatus() + ", to: " + statusFlow.getLockState() + " for Charge ID: " + chargeEntity.getId());
            reloadedCharge.setStatus(statusFlow.getLockState());
            return chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge);
        };
    }

    private NonTransactionalOperation<TransactionContext, GatewayResponse> doGatewayCancel() {
        return context -> {
            ChargeEntity chargeEntity = context.get(ChargeEntity.class);
            return providers.resolve(chargeEntity.getGatewayAccount().getGatewayName())
                    .cancel(CancelRequest.valueOf(chargeEntity));
        };
    }

    private TransactionalOperation<TransactionContext, GatewayResponse> finishCancel(Function<GatewayResponse, ChargeStatus> determineTerminalState) {
        return context -> {
            ChargeEntity chargeEntity = context.get(ChargeEntity.class);
            GatewayResponse cancelResponse = context.get(CancelGatewayResponse.class);
            ChargeStatus updateTo = determineTerminalState.apply(cancelResponse);
            logger.info("charge status to update - from: " + chargeEntity.getStatus() + ", to: " + updateTo + " for Charge ID: " + chargeEntity.getId());
            chargeEntity.setStatus(updateTo);
            chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
            return cancelResponse;
        };
    }

    private Function<GatewayResponse, ChargeStatus> determineTerminalState(ChargeStatus onSuccess, ChargeStatus onFail) {
        return gatewayResponse -> gatewayResponse.isSuccessful() ? onSuccess : onFail;
    }

    private String getLegalStatusNames(ChargeStatus[] legalStatuses) {
        return Stream.of(legalStatuses).map(ChargeStatus::toString).collect(Collectors.joining(", "));
    }

    static class CancellationStatusFlow {

        public static final CancellationStatusFlow USER_CANCELLATION_FLOW = new CancellationStatusFlow(
                new ChargeStatus[]{
                        ENTERING_CARD_DETAILS,
                        AUTHORISATION_SUCCESS,
                        EXPIRE_CANCEL_PENDING //
                },
                USER_CANCEL_READY,
                USER_CANCELLED,
                USER_CANCEL_ERROR
        );

        public static final CancellationStatusFlow SYSTEM_CANCELLATION_FLOW = new CancellationStatusFlow(
                new ChargeStatus[]{
                        CREATED,
                        ENTERING_CARD_DETAILS,
                        AUTHORISATION_SUCCESS
                },
                SYSTEM_CANCEL_READY,
                SYSTEM_CANCELLED,
                SYSTEM_CANCEL_ERROR
        );

        private ChargeStatus[] cancellableStatuses;
        private ChargeStatus lockState;
        private ChargeStatus successTerminalState;
        private ChargeStatus failureTerminalState;

        private CancellationStatusFlow(ChargeStatus[] cancellableStatuses, ChargeStatus lockState, ChargeStatus successTerminalState, ChargeStatus failureTerminalState) {
            this.cancellableStatuses = cancellableStatuses;
            this.lockState = lockState;
            this.successTerminalState = successTerminalState;
            this.failureTerminalState = failureTerminalState;
        }

        public ChargeStatus[] getCancellableStatuses() {
            return cancellableStatuses;
        }

        public ChargeStatus getLockState() {
            return lockState;
        }

        public ChargeStatus getSuccessTerminalState() {
            return successTerminalState;
        }

        public ChargeStatus getFailureTerminalState() {
            return failureTerminalState;
        }
    }
}
