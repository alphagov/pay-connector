package uk.gov.pay.connector.exception;


import javax.ws.rs.WebApplicationException;

import static uk.gov.pay.connector.util.ResponseUtil.conflictErrorResponse;

public class ConflictRuntimeException extends WebApplicationException {
    public ConflictRuntimeException(String message) {
        super(conflictErrorResponse(message));
    }

}