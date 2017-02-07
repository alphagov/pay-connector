package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAuthRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.persistence.OptimisticLockException;
import java.util.Optional;
import java.util.function.Supplier;

import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus;

public abstract class CardAuthoriseBaseService<T extends GatewayAuthRequest> extends CardService<BaseAuthoriseResponse> {

    private final CardExecutorService cardExecutorService;

    public CardAuthoriseBaseService(ChargeDao chargeDao, PaymentProviders providers, CardExecutorService cardExecutorService, MetricRegistry metricRegistry) {
        super(chargeDao, providers, metricRegistry);
        this.cardExecutorService = cardExecutorService;
    }

    @Transactional
    public ChargeEntity preOperation(ChargeEntity chargeEntity) {
        chargeEntity = preOperation(chargeEntity, OperationType.AUTHORISATION, getLegalStates(), AUTHORISATION_READY);
        getPaymentProviderFor(chargeEntity).generateTransactionId().ifPresent(chargeEntity::setGatewayTransactionId);
        return chargeEntity;
    }

    public GatewayResponse doAuthorise(String chargeId, T gatewayAuthRequest) {

        Optional<ChargeEntity> chargeEntityOptional = chargeDao.findByExternalId(chargeId);

        if (chargeEntityOptional.isPresent()) {
            Supplier<GatewayResponse> authorisationSupplier = () -> {
                ChargeEntity chargeEntity = chargeEntityOptional.get();
                ChargeEntity preOperationResponse;
                try {
                    preOperationResponse = preOperation(chargeEntity);
                } catch (OptimisticLockException e) {
                    throw new ConflictRuntimeException(chargeEntity.getExternalId());
                }

                GatewayResponse<BaseAuthoriseResponse> operationResponse = operation(preOperationResponse, gatewayAuthRequest);

                return postOperation(preOperationResponse, gatewayAuthRequest, operationResponse);
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
        } else {
            throw new ChargeNotFoundRuntimeException(chargeId);
        }
    }

    protected abstract GatewayResponse postOperation(ChargeEntity preOperationResponse, T gatewayAuthRequest, GatewayResponse<BaseAuthoriseResponse> operationResponse);

    protected abstract GatewayResponse<BaseAuthoriseResponse> operation(ChargeEntity preOperationResponse, T gatewayAuthRequest);

    protected abstract ChargeStatus[] getLegalStates();

}
