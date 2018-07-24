package uk.gov.pay.connector.service;

import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.domain.AuthorisationDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.persistence.OptimisticLockException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus;

public abstract class CardAuthoriseBaseService<T extends AuthorisationDetails> extends CardService<BaseAuthoriseResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(CardAuthoriseBaseService.class);

    private final CardExecutorService cardExecutorService;

    public CardAuthoriseBaseService(ChargeDao chargeDao, ChargeEventDao chargeEventDao, PaymentProviders providers, CardExecutorService cardExecutorService, Environment environment, ChargeStatusUpdater chargeStatusUpdater) {
        super(chargeDao, chargeEventDao, providers, environment, chargeStatusUpdater);
        this.cardExecutorService = cardExecutorService;
    }

    public GatewayResponse doAuthorise(String chargeId, T gatewayAuthRequest) {

        Supplier authorisationSupplier = () -> {
            ChargeEntity charge;
            try {
                charge = preOperation(chargeId, gatewayAuthRequest);
                if (charge.hasStatus(ChargeStatus.AUTHORISATION_ABORTED)) {
                    throw new ConflictRuntimeException(chargeId, "configuration mismatch");
                }
            } catch (OptimisticLockException e) {
                LOG.info("OptimisticLockException in doAuthorise for charge external_id=" + chargeId);
                throw new ConflictRuntimeException(chargeId);
            }
             GatewayResponse<BaseAuthoriseResponse> operationResponse = operation(charge, gatewayAuthRequest);
            return postOperation(chargeId, gatewayAuthRequest, operationResponse);
        };

        Pair<ExecutionStatus, GatewayResponse> executeResult = cardExecutorService.execute(authorisationSupplier);

        switch (executeResult.getLeft()) {
            case COMPLETED:
                return executeResult.getRight();
            case IN_PROGRESS:
                throw new OperationAlreadyInProgressRuntimeException(OperationType.AUTHORISATION.getValue(), chargeId);
            default:
                throw new GenericGatewayRuntimeException("Exception occurred while doing authorisation");
        }
    }

    protected void setGatewayTransactionId(ChargeEntity chargeEntity, String transactionId) {
        chargeEntity.setGatewayTransactionId(transactionId);
    }

    protected abstract ChargeEntity preOperation(String chargeId, T gatewayAuthRequest);

    protected abstract GatewayResponse postOperation(String chargeId, T gatewayAuthRequest, GatewayResponse<BaseAuthoriseResponse> operationResponse);

    protected abstract GatewayResponse<BaseAuthoriseResponse> operation(ChargeEntity charge, T gatewayAuthRequest);

    protected abstract List<ChargeStatus> getLegalStates();

}
