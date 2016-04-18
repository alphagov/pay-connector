package uk.gov.pay.connector.exception;

import javax.ws.rs.WebApplicationException;

import static uk.gov.pay.connector.util.ResponseUtil.acceptedResponse;

public class OperationAlreadyInProgressRuntimeException extends WebApplicationException {
    public OperationAlreadyInProgressRuntimeException(String message) {
        super(acceptedResponse(message));
    }
}
