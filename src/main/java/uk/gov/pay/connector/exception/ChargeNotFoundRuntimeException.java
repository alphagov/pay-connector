package uk.gov.pay.connector.exception;

import javax.ws.rs.WebApplicationException;

import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class ChargeNotFoundRuntimeException extends WebApplicationException {
    public ChargeNotFoundRuntimeException(String message) {
        super(notFoundResponse(message));
    }
}
