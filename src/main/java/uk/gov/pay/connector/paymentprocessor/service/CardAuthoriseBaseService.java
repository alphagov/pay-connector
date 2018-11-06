package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.gateway.handler.AuthorisationHandler;
import uk.gov.pay.connector.gateway.handler.GatewayHandler;
import uk.gov.pay.connector.gateway.model.AuthorisationDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import java.util.Optional;
import java.util.function.Supplier;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus;

public abstract class CardAuthoriseBaseService<T extends AuthorisationDetails> {

    private final CardExecutorService cardExecutorService;
    protected final ChargeService chargeService;
    private final GatewayHandler gatewayHandler;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected MetricRegistry metricRegistry;

    CardAuthoriseBaseService(GatewayHandler gatewayHandler,
                             CardExecutorService cardExecutorService,
                             ChargeService chargeService,
                             Environment environment) {
        this.gatewayHandler = gatewayHandler;
        this.cardExecutorService = cardExecutorService;
        this.chargeService = chargeService;
        this.metricRegistry = environment.metrics();
    }

    public GatewayResponse<BaseAuthoriseResponse> doAuthorise(String chargeId, T gatewayAuthRequest) {
        Supplier authorisationSupplier = () -> {
            final ChargeEntity charge = prepareChargeForAuthorisation(chargeId, gatewayAuthRequest);
            GatewayResponse<BaseAuthoriseResponse> operationResponse = authorise(charge, gatewayAuthRequest);
            processGatewayAuthorisationResponse(
                    charge.getExternalId(),
                    ChargeStatus.fromString(charge.getStatus()),
                    gatewayAuthRequest,
                    operationResponse);
            
            return operationResponse;
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

    abstract ChargeEntity prepareChargeForAuthorisation(String chargeId, T gatewayAuthRequest);

    abstract void processGatewayAuthorisationResponse(String chargeExternalId, ChargeStatus oldStatus, T gatewayAuthRequest, GatewayResponse<BaseAuthoriseResponse> operationResponse);

    abstract GatewayResponse<BaseAuthoriseResponse> authorise(ChargeEntity charge, T gatewayAuthRequest);

    ChargeStatus extractChargeStatus(Optional<BaseAuthoriseResponse> baseResponse,
                                     Optional<GatewayError> gatewayError) {

        return baseResponse
                .map(BaseAuthoriseResponse::authoriseStatus)
                .map(AuthoriseStatus::getMappedChargeStatus)
                .orElseGet(() -> gatewayError
                        .map(this::mapError)
                        .orElse(ChargeStatus.AUTHORISATION_ERROR));
    }

    AuthorisationHandler<BaseAuthoriseResponse> getAuthorisationProviderFor(ChargeEntity chargeEntity) {
        return gatewayHandler.getAuthorisationHandler(chargeEntity.getPaymentGatewayName());
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
}
