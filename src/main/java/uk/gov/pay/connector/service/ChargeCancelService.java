package uk.gov.pay.connector.service;

import com.google.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.transaction.TransactionContext;
import uk.gov.pay.connector.service.transaction.TransactionFlow;
import uk.gov.pay.connector.service.transaction.TransactionalOperation;
import uk.gov.pay.connector.service.worldpay.WorldpayCancelResponse;

import javax.inject.Inject;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.GatewayError.baseError;
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
    private final ChargeCardDetailsService chargeCardDetailsService;


    @Inject
    public ChargeCancelService(ChargeDao chargeDao,
                               PaymentProviders providers,
                               Provider<TransactionFlow> transactionFlowProvider,
                               ChargeCardDetailsService chargeCardDetailsService) {
        this.chargeDao = chargeDao;
        this.providers = providers;
        this.transactionFlowProvider = transactionFlowProvider;
        this.chargeCardDetailsService = chargeCardDetailsService;
    }

    public Optional<GatewayResponse<BaseCancelResponse>> doSystemCancel(String chargeId, Long accountId) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> doCancel(chargeEntity, SYSTEM_CANCELLATION_FLOW))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    public Optional<GatewayResponse<BaseCancelResponse>> doUserCancel(String chargeId) {
        return chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> doCancel(chargeEntity, USER_CANCELLATION_FLOW))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private Optional<GatewayResponse<BaseCancelResponse>> doCancel(ChargeEntity chargeEntity, StatusFlow statusFlow) {
        if (chargeEntity.hasStatus(nonGatewayStatuses)) {
            return Optional.of(nonGatewayCancel(chargeEntity, statusFlow));
        } else {
            return cancelChargeWithGatewayCleanup(chargeEntity, statusFlow);
        }
    }

    private Optional<GatewayResponse<BaseCancelResponse>> cancelChargeWithGatewayCleanup(ChargeEntity charge, StatusFlow statusFlow) {
        return Optional.ofNullable(transactionFlowProvider.get()
                .executeNext(prepareForTerminate(chargeDao, charge, statusFlow, chargeCardDetailsService))
                .executeNext(doGatewayCancel(providers))
                .executeNext(finishCancel(statusFlow))
                .complete()
                .get(GatewayResponse.class));
    }

    private TransactionalOperation<TransactionContext, GatewayResponse<BaseCancelResponse>> finishCancel(StatusFlow statusFlow) {
        return context -> {
            ChargeEntity chargeEntity = context.get(ChargeEntity.class);
            GatewayResponse cancelResponse = context.get(GatewayResponse.class);
            ChargeStatus status = determineTerminalState(cancelResponse, statusFlow);

            logger.info("Card cancel response received - charge_external_id={}, transaction_id={}, status={}",
                    chargeEntity.getExternalId(), chargeEntity.getGatewayTransactionId(), status);

            logger.info("Charge status to update - charge_external_id={}, status={}, to_status={}",
                    chargeEntity.getExternalId(), chargeEntity.getStatus(), status);

            chargeEntity.setStatus(status);
            chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
            return cancelResponse;
        };
    }

    private ChargeStatus determineTerminalState(GatewayResponse cancelResponse, StatusFlow statusFlow) {
        return cancelResponse.isSuccessful() ? statusFlow.getSuccessTerminalState() : statusFlow.getFailureTerminalState();
    }

    private GatewayResponse<BaseCancelResponse> nonGatewayCancel(ChargeEntity chargeEntity, StatusFlow statusFlow) {
        ChargeStatus completeStatus = statusFlow.getSuccessTerminalState();
        ChargeEntity processedCharge = transactionFlowProvider.get()
                .executeNext(changeStatusTo(chargeDao, chargeEntity, completeStatus))
                .complete()
                .get(ChargeEntity.class);

        if (completeStatus.getValue().equals(processedCharge.getStatus())) {
            return GatewayResponse.with(new WorldpayCancelResponse());
        } else {
            String errorMsg = format("Could not update chargeId [%s] to status [%s]. Current state [%s]",
                    processedCharge.getExternalId(),
                    completeStatus,
                    processedCharge.getStatus());
            logger.error("Could not update charge - charge_external-id={}, status={}, to_status={}",
                    processedCharge.getExternalId(), processedCharge.getStatus(), completeStatus);
            return GatewayResponse.with(baseError(errorMsg));
        }
    }

}
