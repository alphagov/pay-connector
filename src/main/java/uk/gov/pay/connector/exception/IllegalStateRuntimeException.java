package uk.gov.pay.connector.exception;

import javax.ws.rs.WebApplicationException;

import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

public class IllegalStateRuntimeException extends WebApplicationException {
    public IllegalStateRuntimeException(String message) {
        super(serviceErrorResponse(message));
    }
}