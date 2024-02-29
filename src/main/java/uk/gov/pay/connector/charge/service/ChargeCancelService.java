package uk.gov.pay.connector.charge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ExpirableChargeStatus;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.paymentprocessor.service.QueryService;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.charge.service.StatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.charge.service.StatusFlow.USER_CANCELLATION_FLOW;

public class ChargeCancelService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    private final ChargeDao chargeDao;
    private final PaymentProviders providers;
    private final ChargeService chargeService;
    private final QueryService queryService;

    @Inject
    public ChargeCancelService(ChargeDao chargeDao,
                               PaymentProviders providers,
                               ChargeService chargeService,
                               QueryService queryService) {
        this.chargeDao = chargeDao;
        this.providers = providers;
        this.chargeService = chargeService;
        this.queryService = queryService;
    }

    public Optional<ChargeEntity> doSystemCancel(String chargeId, Long accountId) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> {
                    doCancel(chargeEntity, SYSTEM_CANCELLATION_FLOW);
                    return chargeEntity;
                });
    }

    public Optional<ChargeEntity> doUserCancel(String chargeId) {
        return chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> {
                    doCancel(chargeEntity, USER_CANCELLATION_FLOW);
                    return chargeEntity;
                });
    }

    private void doCancel(ChargeEntity chargeEntity, StatusFlow statusFlow) {
        
        validateChargeStatus(statusFlow, chargeEntity);
        
        ChargeStatus chargeStatus = ChargeStatus.fromString(chargeEntity.getStatus());

        ExpirableChargeStatus.AuthorisationStage authorisationStage = ExpirableChargeStatus
                .of(chargeStatus).getAuthorisationStage();

        boolean cancellableWithGateway = authorisationStage == ExpirableChargeStatus.AuthorisationStage.POST_AUTHORISATION
                || (authorisationStage == ExpirableChargeStatus.AuthorisationStage.DURING_AUTHORISATION
                && queryService.isTerminableWithGateway(chargeEntity));

        if (cancellableWithGateway) {
            cancelChargeWithGatewayCleanup(chargeEntity, statusFlow);
        } else {
            nonGatewayCancel(chargeEntity, statusFlow);
        }
    }

    private void cancelChargeWithGatewayCleanup(ChargeEntity chargeEntity, StatusFlow statusFlow) {
        prepareForTerminate(chargeEntity, statusFlow);

        ChargeStatus chargeStatus;
        String stringifiedResponse;

        try {
            final GatewayResponse<BaseCancelResponse> gatewayResponse = doGatewayCancel(chargeEntity);

            if (gatewayResponse.getBaseResponse().isEmpty()) {
                gatewayResponse.throwGatewayError();
            }
            
            chargeStatus = determineTerminalState(gatewayResponse.getBaseResponse().get(), statusFlow);
            stringifiedResponse = gatewayResponse.getBaseResponse().get().toString();
        } catch (GatewayException e) {
            logger.error(e.getMessage());
            chargeStatus = statusFlow.getFailureTerminalState();
            stringifiedResponse = e.getMessage();
        }

        logger.info("Cancel for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                chargeEntity.getExternalId(), chargeEntity.getPaymentGatewayName().getName(), chargeEntity.getGatewayTransactionId(),
                chargeEntity.getGatewayAccount().getAnalyticsId(), chargeEntity.getGatewayAccount().getId(),
                stringifiedResponse, chargeEntity.getStatus(), chargeStatus);

        chargeService.transitionChargeState(chargeEntity.getExternalId(), chargeStatus);
    }

    private GatewayResponse<BaseCancelResponse> doGatewayCancel(ChargeEntity chargeEntity) throws GatewayException {
        return providers.byName(chargeEntity.getPaymentGatewayName()).cancel(CancelGatewayRequest.valueOf(chargeEntity));
    }

    private static ChargeStatus determineTerminalState(BaseCancelResponse response, StatusFlow statusFlow) {
        switch (response.cancelStatus()) {
            case CANCELLED:
                return statusFlow.getSuccessTerminalState();
            case SUBMITTED:
                return statusFlow.getSubmittedState();
            default:
                return statusFlow.getFailureTerminalState();
        }
    }

    private void nonGatewayCancel(ChargeEntity chargeEntity, StatusFlow statusFlow) {
        ChargeStatus completeStatus = statusFlow.getSuccessTerminalState();

        logger.info("Charge status to update - charge_external_id={}, status={}, to_status={}",
                chargeEntity.getExternalId(), chargeEntity.getStatus(), completeStatus);

        chargeService.transitionChargeState(chargeEntity.getExternalId(), completeStatus);
    }
    
    private void prepareForTerminate(ChargeEntity chargeEntity, StatusFlow statusFlow) {
        ChargeStatus lockState = statusFlow.getLockState();
        ChargeStatus currentStatus = ChargeStatus.fromString(chargeEntity.getStatus());
        
        // Used by Sumo Logic saved search
        logger.info("Card cancel request sent - charge_external_id={}, charge_status={}, account_id={}, transaction_id={}, amount={}, operation_type={}, provider={}, provider_type={}, locking_status={}",
                chargeEntity.getExternalId(),
                currentStatus,
                chargeEntity.getGatewayAccount().getId(),
                chargeEntity.getGatewayTransactionId(),
                chargeEntity.getAmount(),
                OperationType.CANCELLATION.getValue(),
                chargeEntity.getPaymentProvider(),
                chargeEntity.getGatewayAccount().getType(),
                lockState);

        chargeService.transitionChargeState(chargeEntity.getExternalId(), lockState);
    }

    private void validateChargeStatus(StatusFlow statusFlow, ChargeEntity chargeEntity) {
        ChargeStatus chargeStatus = ChargeStatus.fromString(chargeEntity.getStatus());

        if (statusFlow.isInProgress(chargeStatus)) {
            throw new OperationAlreadyInProgressRuntimeException(statusFlow.getName(), chargeEntity.getExternalId());
        }
        
        if (!chargeIsInTerminableStatus(statusFlow, chargeStatus)) {
            logger.info("Charge is not in one of the legal states. charge_external_id={}, status={}, legal_states={}",
                    chargeEntity.getExternalId(), chargeEntity.getStatus(), getLegalStatusNames(statusFlow.getTerminatableStatuses()));

            throw new IllegalStateRuntimeException(chargeEntity.getExternalId());
        }
    }

    private static boolean chargeIsInTerminableStatus(StatusFlow statusFlow, ChargeStatus chargeStatus) {
        return statusFlow.getTerminatableStatuses().contains(chargeStatus);
    }

    private static String getLegalStatusNames(List<ChargeStatus> legalStatuses) {
        return legalStatuses.stream().map(ChargeStatus::toString).collect(Collectors.joining(", "));
    }
}
