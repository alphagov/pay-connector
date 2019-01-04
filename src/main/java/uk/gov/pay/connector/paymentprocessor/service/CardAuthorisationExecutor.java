package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.util.XrayUtils;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;

public class CardAuthorisationExecutor {

    private static final int QUEUE_WAIT_WARN_THRESHOLD_MILLIS = 10000;
    
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final MetricRegistry metricRegistry;
    private final XrayUtils xrayUtils;

    @Inject
    public CardAuthorisationExecutor(Environment environment, XrayUtils xrayUtils) {
        this.metricRegistry = environment.metrics();
        this.xrayUtils = xrayUtils;
    }
    
    public <T> T executeAuthorise(String chargeId, Supplier<T> f) {
        final long startTime = System.currentTimeMillis();
        xrayUtils.beginSegment();

        try {
            return CompletableFuture.supplyAsync(() -> {
                long totalWaitTime = System.currentTimeMillis() - startTime;
                logger.debug("Card operation task spent {} ms in queue", totalWaitTime);
                if (totalWaitTime > QUEUE_WAIT_WARN_THRESHOLD_MILLIS) {
                    logger.warn("CardExecutor Service delay - queue_wait_time={}", totalWaitTime);
                }
                metricRegistry.histogram("card-executor.delay").update(totalWaitTime);
                return f.get();
            }).get(1000, TimeUnit.SECONDS);

        } catch (InterruptedException | ExecutionException exception) {
            if (exception.getCause() instanceof WebApplicationException) {
                throw (WebApplicationException) exception.getCause();
            } else if (exception.getCause() instanceof UnsupportedOperationException) {
                throw (UnsupportedOperationException) exception.getCause();
            }
            throw new GenericGatewayRuntimeException("Exception occurred while doing authorisation");
        } catch (TimeoutException e) {
            throw new OperationAlreadyInProgressRuntimeException(OperationType.AUTHORISATION.getValue(), chargeId);
        } finally {
            xrayUtils.endSegment();
        }
    }
    
    public ChargeStatus extractChargeStatus(Optional<BaseAuthoriseResponse> baseResponse,
                                     Optional<GatewayError> gatewayError) {
        return baseResponse
                .map(BaseAuthoriseResponse::authoriseStatus)
                .map(BaseAuthoriseResponse.AuthoriseStatus::getMappedChargeStatus)
                .orElseGet(() -> gatewayError
                        .map(this::mapError)
                        .orElse(ChargeStatus.AUTHORISATION_ERROR));
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

    private ChargeStatus mapError(GatewayError gatewayError) {
        switch (gatewayError.getErrorType()) {
            case GENERIC_GATEWAY_ERROR:
                return AUTHORISATION_ERROR;
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
                return AUTHORISATION_TIMEOUT;
            default:
                return AUTHORISATION_UNEXPECTED_ERROR;
        }
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
}
