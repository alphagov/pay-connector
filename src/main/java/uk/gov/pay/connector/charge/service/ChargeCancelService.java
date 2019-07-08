package uk.gov.pay.connector.charge.service;

import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.service.StatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.charge.service.StatusFlow.USER_CANCELLATION_FLOW;

public class ChargeCancelService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static List<ChargeStatus> nonGatewayStatuses = ImmutableList.of(
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_3DS_REQUIRED
    );

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final PaymentProviders providers;

    @Inject
    public ChargeCancelService(ChargeDao chargeDao,
                               ChargeEventDao chargeEventDao,
                               PaymentProviders providers,
                               ChargeService cha) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.providers = providers;
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
        if (gatewayIsNotAwareOfCharge(chargeEntity)) {
            nonGatewayCancel(chargeEntity, statusFlow);
        } else {
            cancelChargeWithGatewayCleanup(chargeEntity, statusFlow);
        }
    }

    private void cancelChargeWithGatewayCleanup(ChargeEntity chargeEntity, StatusFlow statusFlow) {
        prepareForTerminate(chargeEntity, statusFlow);

        ChargeStatus chargeStatus = null;
        String stringifiedResponse = null;

        try {
            final GatewayResponse<BaseCancelResponse> gatewayResponse = doGatewayCancel(chargeEntity);
            
            if (!gatewayResponse.getBaseResponse().isPresent()) gatewayResponse.throwGatewayError();
            
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

        setChargeStatusTo(chargeEntity.getExternalId(), chargeStatus);
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

        setChargeStatusTo(chargeEntity.getExternalId(), completeStatus);
    }

    private boolean gatewayIsNotAwareOfCharge(ChargeEntity chargeEntity) {
        return nonGatewayStatuses.contains(ChargeStatus.fromString(chargeEntity.getStatus()));
    }

    private void prepareForTerminate(ChargeEntity chargeEntity, StatusFlow statusFlow) {
        ChargeStatus newStatus = statusFlow.getLockState();
        ChargeStatus currentStatus = ChargeStatus.fromString(chargeEntity.getStatus());

        validateChargeStatus(statusFlow, chargeEntity, newStatus, currentStatus);

        // Used by Sumo Logic saved search
        logger.info("Card cancel request sent - charge_external_id={}, charge_status={}, account_id={}, transaction_id={}, amount={}, operation_type={}, provider={}, provider_type={}, locking_status={}",
                chargeEntity.getExternalId(),
                currentStatus,
                chargeEntity.getGatewayAccount().getId(),
                chargeEntity.getGatewayTransactionId(),
                chargeEntity.getAmount(),
                OperationType.CANCELLATION.getValue(),
                chargeEntity.getGatewayAccount().getGatewayName(),
                chargeEntity.getGatewayAccount().getType(),
                newStatus);

        setChargeStatusTo(chargeEntity.getExternalId(), newStatus);
    }

    @Transactional
    @SuppressWarnings("WeakerAccess") // Guice requires @Transactional methods to be public
    public void setChargeStatusTo(String chargeEntityExternalId, ChargeStatus chargeStatus) {
        chargeDao.findByExternalId(chargeEntityExternalId).ifPresentOrElse(chargeEntity -> {
            
            chargeEntity.setStatus(chargeStatus);
            chargeEventDao.persistChargeEventOf(chargeEntity);
        }, () -> {
            throw new ChargeNotFoundRuntimeException(chargeEntityExternalId);
        });
    }

    private void validateChargeStatus(StatusFlow statusFlow, ChargeEntity chargeEntity, ChargeStatus newStatus, ChargeStatus oldStatus) {
        if (!chargeIsInTerminatableStatus(statusFlow, oldStatus)) {
            if (newStatus.equals(oldStatus)) {
                throw new OperationAlreadyInProgressRuntimeException(statusFlow.getName(), chargeEntity.getExternalId());
            } else if (Arrays.asList(AUTHORISATION_READY, AUTHORISATION_3DS_READY).contains(oldStatus)) {
                throw new ConflictRuntimeException(chargeEntity.getExternalId());
            }

            logger.error("Charge is not in one of the legal states. charge_external_id={}, status={}, legal_states={}",
                    chargeEntity.getExternalId(), chargeEntity.getStatus(), getLegalStatusNames(statusFlow.getTerminatableStatuses()));

            throw new IllegalStateRuntimeException(chargeEntity.getExternalId());
        }
    }

    private static boolean chargeIsInTerminatableStatus(StatusFlow statusFlow, ChargeStatus chargeStatus) {
        return statusFlow.getTerminatableStatuses().contains(chargeStatus);
    }

    private static String getLegalStatusNames(List<ChargeStatus> legalStatuses) {
        return legalStatuses.stream().map(ChargeStatus::toString).collect(Collectors.joining(", "));
    }
}
