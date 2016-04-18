package uk.gov.pay.connector.exception;

import static uk.gov.pay.connector.util.ResponseUtil.acceptedResponse;

public class OperationAlreadyInProgressRuntimeException extends ConnectorRuntimeException {
    public OperationAlreadyInProgressRuntimeException(String message) {
        super(acceptedResponse(message));
    }
}
