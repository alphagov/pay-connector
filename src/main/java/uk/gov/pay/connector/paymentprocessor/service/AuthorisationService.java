package uk.gov.pay.connector.paymentprocessor.service;


import com.codahale.metrics.MetricRegistry;
import io.dropwizard.core.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.exception.AuthorisationExecutorTimedOutException;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import jakarta.inject.Inject;
import java.util.Optional;
import java.util.function.Supplier;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus;

public class AuthorisationService {

    private final CardExecutorService cardExecutorService;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final MetricRegistry metricRegistry;
    private final AuthorisationConfig authorisationConfig;

    @Inject
    public AuthorisationService(CardExecutorService cardExecutorService, Environment environment, ConnectorConfiguration configuration) {
        this.cardExecutorService = cardExecutorService;
        this.metricRegistry = environment.metrics();
        this.authorisationConfig = configuration.getAuthorisationConfig();
    }

    public <T> T executeAuthorise(String chargeId, Supplier<T> authorisationSupplier) {
        int timeoutInMilliseconds = authorisationConfig.getAsynchronousAuthTimeoutInMilliseconds();
        try {
            return executeAuthorise(authorisationSupplier, timeoutInMilliseconds);
        } catch (AuthorisationExecutorTimedOutException e) {
            // Exception is mapped to a success response and authorisation is allowed to continue in background thread.
            throw new OperationAlreadyInProgressRuntimeException(OperationType.AUTHORISATION.getValue(), chargeId);
        }
    }

    public <T> T executeAuthoriseSync(Supplier<T> authorisationSupplier) throws AuthorisationExecutorTimedOutException {
        int timeoutInMilliseconds = authorisationConfig.getSynchronousAuthTimeoutInMilliseconds();
        return executeAuthorise(authorisationSupplier, timeoutInMilliseconds);
    }

    private <T> T executeAuthorise(Supplier<T> authorisationSupplier, int timeoutInMilliseconds)
            throws AuthorisationExecutorTimedOutException {
        Pair<ExecutionStatus, T> executeResult = cardExecutorService.execute(authorisationSupplier, timeoutInMilliseconds);

        switch (executeResult.getLeft()) {
            case COMPLETED:
                return executeResult.getRight();
            case IN_PROGRESS:
                throw new AuthorisationExecutorTimedOutException("Timeout while waiting for authorisation to complete");
            default:
                logger.info("Exception occurred while doing authorisation left: {}", executeResult.getLeft());
                logger.info("Exception occurred while doing authorisation right: {}", executeResult.getRight());
                throw new GenericGatewayRuntimeException("Exception occurred while doing authorisation");
        }
    }

    public Optional<String> extractTransactionId(String chargeExternalId,
                                                 GatewayResponse<? extends BaseAuthoriseResponse> operationResponse,
                                                 String currentGatewayTransactionId) {
        Optional<String> transactionId = operationResponse.getBaseResponse()
                .map(BaseAuthoriseResponse::getTransactionId);

        if (transactionId.isEmpty() || StringUtils.isBlank(transactionId.get())) {
            logger.warn("AuthCardDetails authorisation response received with no transaction id. -  charge_external_id={}",
                    chargeExternalId);
            return Optional.ofNullable(currentGatewayTransactionId);
        }

        return transactionId;
    }

    void emitAuthorisationMetric(ChargeEntity charge, String operation) {
        metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.result.%s",
                charge.getPaymentProvider(),
                charge.getGatewayAccount().getType(),
                operation,
                charge.getStatus())
        ).inc();
    }

    public static ChargeStatus mapFromGatewayErrorException(GatewayException e) {
        if (e instanceof GatewayException.GenericGatewayException) return AUTHORISATION_ERROR;
        if (e instanceof GatewayException.GatewayConnectionTimeoutException) return AUTHORISATION_TIMEOUT;
        if (e instanceof GatewayException.GatewayErrorException) return AUTHORISATION_UNEXPECTED_ERROR;
        throw new RuntimeException("Unrecognised GatewayException instance " + e.getClass());
    }
}
