package uk.gov.pay.connector.service;

import com.google.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.model.CancelGatewayResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.transaction.TransactionContext;
import uk.gov.pay.connector.service.transaction.TransactionFlow;
import uk.gov.pay.connector.service.transaction.TransactionalOperation;

import javax.inject.Inject;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.CancelGatewayResponse.successfulCancelResponse;
import static uk.gov.pay.connector.model.ErrorResponse.baseError;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.FAILED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.service.CancelServiceFunctions.*;
import static uk.gov.pay.connector.service.StatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.service.StatusFlow.USER_CANCELLATION_FLOW;

public class ChargeCancelService {

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

    private Optional<GatewayResponse> doCancel(ChargeEntity chargeEntity, StatusFlow statusFlow) {
        if (chargeEntity.hasStatus(nonGatewayStatuses)) {
            return Optional.of(nonGatewayCancel(chargeEntity, statusFlow));
        } else {
            return cancelChargeWithGatewayCleanup(chargeEntity, statusFlow);
        }
    }

    private Optional<GatewayResponse> cancelChargeWithGatewayCleanup(ChargeEntity charge, StatusFlow statusFlow) {
        return Optional.ofNullable(transactionFlowProvider.get()
                .executeNext(prepareForTerminate(chargeDao, charge, statusFlow))
                .executeNext(doGatewayCancel(providers))
                .executeNext(finishCancel(statusFlow))
                .complete()
                .get(CancelGatewayResponse.class));

    }

    private TransactionalOperation<TransactionContext, GatewayResponse> finishCancel(StatusFlow statusFlow) {
        return context -> {
            ChargeEntity chargeEntity = context.get(ChargeEntity.class);
            GatewayResponse cancelResponse = context.get(CancelGatewayResponse.class);
            ChargeStatus updateTo = determineTerminalState(cancelResponse, statusFlow);
            logger.info("charge status to update - from: {}, to: {} for Charge ID: {}",
                    chargeEntity.getStatus(), updateTo, chargeEntity.getId());
            chargeEntity.setStatus(updateTo);
            chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
            return cancelResponse;
        };
    }

    private ChargeStatus determineTerminalState(GatewayResponse cancelResponse, StatusFlow statusFlow) {
        return cancelResponse.isSuccessful() ? statusFlow.getSuccessTerminalState() : statusFlow.getFailureTerminalState();
    }

    private GatewayResponse nonGatewayCancel(ChargeEntity chargeEntity, StatusFlow statusFlow) {
        ChargeStatus completeStatus = statusFlow.getSuccessTerminalState();
        ChargeEntity processedCharge = transactionFlowProvider.get()
                .executeNext(changeStatusTo(chargeDao, chargeEntity, completeStatus))
                .complete()
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

}
