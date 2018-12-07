package uk.gov.pay.connector.paymentprocessor.service;


import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.gateway.model.GatewayError;
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
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public CardAuthoriseBaseService(CardExecutorService cardExecutorService) {
        this.cardExecutorService = cardExecutorService;
    }


    public <T> T executeAuthorise(String chargeId, Supplier<T> authorisationSupplier) {
        Pair<ExecutionStatus, T> executeResult = (Pair<ExecutionStatus, T>) cardExecutorService.execute(authorisationSupplier);

        switch (executeResult.getLeft()) {
            case COMPLETED:
                return executeResult.getRight();
            case IN_PROGRESS:
                throw new OperationAlreadyInProgressRuntimeException(OperationType.AUTHORISATION.getValue(), chargeId);
            default:
                throw new GenericGatewayRuntimeException("Exception occurred while doing authorisation");
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
}
