package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.service.transaction.TransactionContext;
import uk.gov.pay.connector.service.transaction.TransactionFlow;
import uk.gov.pay.connector.service.transaction.TransactionalOperation;
import uk.gov.pay.connector.service.worldpay.WorldpayCancelResponse;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.GatewayError.baseError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.service.CancelServiceFunctions.changeStatusTo;
import static uk.gov.pay.connector.service.CancelServiceFunctions.doGatewayCancel;
import static uk.gov.pay.connector.service.CancelServiceFunctions.prepareForTerminate;
import static uk.gov.pay.connector.service.StatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.service.StatusFlow.USER_CANCELLATION_FLOW;

public class ChargeCancelService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static List<ChargeStatus> nonGatewayStatuses = ImmutableList.of(
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_3DS_REQUIRED
    );

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final PaymentProviders providers;
    private final Provider<TransactionFlow> transactionFlowProvider;

    @Inject
    public ChargeCancelService(ChargeDao chargeDao,
                               ChargeEventDao chargeEventDao,
                               PaymentProviders providers,
                               Provider<TransactionFlow> transactionFlowProvider) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.providers = providers;
        this.transactionFlowProvider = transactionFlowProvider;
    }

    public Optional<GatewayResponse<BaseCancelResponse>> doSystemCancel(String chargeId, Long accountId) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(doCancel(chargeId, SYSTEM_CANCELLATION_FLOW))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    public Optional<GatewayResponse<BaseCancelResponse>> doUserCancel(String chargeId) {
        return chargeDao.findByExternalId(chargeId)
                .map(doCancel(chargeId, USER_CANCELLATION_FLOW))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private Function<ChargeEntity, Optional<GatewayResponse<BaseCancelResponse>>> doCancel(String chargeId, StatusFlow statusFlow) {
        return chargeEntity -> {
            if (chargeEntity.hasStatus(nonGatewayStatuses)) {
                return Optional.of(nonGatewayCancel(chargeId, statusFlow));
            } else {
                return cancelChargeWithGatewayCleanup(chargeId, statusFlow);
            }
        };
    }

    private Optional<GatewayResponse<BaseCancelResponse>> cancelChargeWithGatewayCleanup(String chargeId, StatusFlow statusFlow) {
        return Optional.ofNullable(transactionFlowProvider.get()
                .executeNext(prepareForTerminate(chargeDao, chargeEventDao, chargeId, statusFlow))
                .executeNext(doGatewayCancel(providers))
                .executeNext(finishCancel(chargeId, statusFlow))
                .complete()
                .get(GatewayResponse.class));
    }

    private TransactionalOperation<TransactionContext, GatewayResponse<BaseCancelResponse>> finishCancel(String chargeId, StatusFlow statusFlow) {
        return context -> chargeDao.findByExternalId(chargeId).map(chargeEntity -> {
            GatewayResponse cancelResponse = context.get(GatewayResponse.class);
            ChargeStatus status = determineTerminalState(cancelResponse, statusFlow);

            logger.info("Cancel for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    chargeEntity.getExternalId(), chargeEntity.getPaymentGatewayName().getName(), chargeEntity.getGatewayTransactionId(),
                    chargeEntity.getGatewayAccount().getAnalyticsId(), chargeEntity.getGatewayAccount().getId(),
                    cancelResponse, chargeEntity.getStatus(), status);

            chargeEntity.setStatus(status);
            chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());
            return cancelResponse;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private ChargeStatus determineTerminalState(GatewayResponse<BaseCancelResponse> cancelResponse, StatusFlow statusFlow) {
        return cancelResponse.getBaseResponse().map(response -> {
            switch (response.cancelStatus()) {
                case CANCELLED:
                    return statusFlow.getSuccessTerminalState();
                case SUBMITTED:
                    return statusFlow.getSubmittedState();
                default:
                    return statusFlow.getFailureTerminalState();
            }
        }).orElse(statusFlow.getFailureTerminalState());
    }

    private GatewayResponse<BaseCancelResponse> nonGatewayCancel(String chargeId, StatusFlow statusFlow) {
        ChargeStatus completeStatus = statusFlow.getSuccessTerminalState();
        ChargeEntity processedCharge = transactionFlowProvider.get()
                .executeNext(changeStatusTo(chargeDao, chargeEventDao, chargeId, completeStatus, Optional.empty()))
                .complete()
                .get(ChargeEntity.class);
        GatewayResponseBuilder<BaseCancelResponse> gatewayResponseBuilder = responseBuilder();

        if (completeStatus.getValue().equals(processedCharge.getStatus())) {
            return gatewayResponseBuilder
                    .withResponse(new WorldpayCancelResponse())
                    .build();
        } else {
            String errorMsg = format("Could not update chargeId [%s] to status [%s]. Current state [%s]",
                    processedCharge.getExternalId(),
                    completeStatus,
                    processedCharge.getStatus());
            logger.error("Could not update charge - charge_external-id={}, status={}, to_status={}",
                    processedCharge.getExternalId(), processedCharge.getStatus(), completeStatus);
            return gatewayResponseBuilder
                    .withGatewayError(baseError(errorMsg))
                    .build();
        }
    }

}
