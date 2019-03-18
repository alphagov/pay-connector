package uk.gov.pay.connector.paymentprocessor.service;


import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.GatewayErrorException;
import uk.gov.pay.connector.gateway.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.Supplier;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus;

public class CardAuthoriseBaseService {
    
    private final CardExecutorService cardExecutorService;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final MetricRegistry metricRegistry;

    @Inject
    public CardAuthoriseBaseService(CardExecutorService cardExecutorService, Environment environment) {
        this.cardExecutorService = cardExecutorService;
        this.metricRegistry = environment.metrics();
    }
 
    public <T> T executeAuthorise(String chargeId, Supplier<T> authorisationSupplier) {
        Pair<ExecutionStatus, T> executeResult = cardExecutorService.execute(authorisationSupplier);

        switch (executeResult.getLeft()) {
            case COMPLETED:
                return executeResult.getRight();
            case IN_PROGRESS:
                throw new OperationAlreadyInProgressRuntimeException(OperationType.AUTHORISATION.getValue(), chargeId);
            default:
                throw new GenericGatewayRuntimeException("Exception occurred while doing authorisation");
        }
    }
    
    public Optional<String> extractTransactionId(String chargeExternalId, GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        Optional<String> transactionId = operationResponse.getBaseResponse()
                .map(BaseAuthoriseResponse::getTransactionId);

        if (!transactionId.isPresent() || StringUtils.isBlank(transactionId.get())) {
            logger.warn("AuthCardDetails authorisation response received with no transaction id. -  charge_external_id={}",
                    chargeExternalId);
        }

        return transactionId;
    }

    void emitAuthorisationMetric(ChargeEntity charge, String operation) {
        metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.%s.result.%s",
                charge.getGatewayAccount().getGatewayName(),
                charge.getGatewayAccount().getType(),
                charge.getGatewayAccount().getId(),
                operation,
                charge.getStatus())
        ).inc();
    }
    
    public static ChargeStatus mapFromGatewayErrorException(GatewayErrorException e) {
        if (e instanceof GatewayErrorException.GenericGatewayErrorException) return AUTHORISATION_ERROR;
        if (e instanceof GatewayErrorException.GatewayConnectionTimeoutErrorException) return AUTHORISATION_TIMEOUT;
        if (e instanceof GatewayErrorException.ClientErrorException) return AUTHORISATION_UNEXPECTED_ERROR;
        if (e instanceof GatewayErrorException.DownstreamErrorException) return AUTHORISATION_UNEXPECTED_ERROR;
        throw new RuntimeException("Unrecognised GatewayErrorException instance " + e.getClass());
    }
}
