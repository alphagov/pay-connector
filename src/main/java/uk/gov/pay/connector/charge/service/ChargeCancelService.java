package uk.gov.pay.connector.charge.service;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.transaction.TransactionContext;
import uk.gov.pay.connector.charge.service.transaction.TransactionFlow;
import uk.gov.pay.connector.charge.service.transaction.TransactionalOperation;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.service.CancelServiceFunctions.doGatewayCancel;
import static uk.gov.pay.connector.charge.service.CancelServiceFunctions.prepareForTerminate;
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

    @Transactional
    public void doSystemCancel(String chargeId, Long accountId) {
        final Optional<ChargeEntity> maybeCharge = chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId);
        if (maybeCharge.isPresent()) {
            doCancel(maybeCharge.get(), SYSTEM_CANCELLATION_FLOW);
        } else {
            throw new ChargeNotFoundRuntimeException(chargeId);
        }
    }

    @Transactional
    public void doUserCancel(String chargeId) {
        final Optional<ChargeEntity> maybeCharge = chargeDao.findByExternalId(chargeId);
        if (maybeCharge.isPresent()) {
            doCancel(maybeCharge.get(), USER_CANCELLATION_FLOW);
        } else {
            throw new ChargeNotFoundRuntimeException(chargeId);
        }
    }

    private void doCancel(ChargeEntity chargeEntity, StatusFlow statusFlow) {
        if (gatewayIsNotAwareOfCharge(chargeEntity)) {
            nonGatewayCancel(chargeEntity, statusFlow);
        } else {
            final Optional<GatewayResponse<BaseCancelResponse>> gatewayResponse = cancelChargeWithGatewayCleanup(chargeEntity.getExternalId(), statusFlow);
            gatewayResponse.ifPresent(r -> r.getGatewayError().ifPresent(gatewayError -> logger.error(gatewayError.getMessage())));
        }
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
            chargeEventDao.persistChargeEventOf(chargeEntity);
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

    private void nonGatewayCancel(ChargeEntity chargeEntity, StatusFlow statusFlow) {
        ChargeStatus completeStatus = statusFlow.getSuccessTerminalState();

        logger.info("Charge status to update - charge_external_id={}, status={}, to_status={}",
                chargeEntity.getExternalId(), chargeEntity.getStatus(), completeStatus);
        chargeEntity.setStatus(completeStatus);
        chargeEventDao.persistChargeEventOf(chargeEntity);
    }

    private boolean gatewayIsNotAwareOfCharge(ChargeEntity chargeEntity) {
        return nonGatewayStatuses.contains(ChargeStatus.fromString(chargeEntity.getStatus()));
    }
}
