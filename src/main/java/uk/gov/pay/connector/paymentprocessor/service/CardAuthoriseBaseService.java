package uk.gov.pay.connector.paymentprocessor.service;


import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import javax.inject.Inject;
import java.util.function.Supplier;

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
}
