package uk.gov.pay.connector.common.exception;


import jakarta.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.conflictErrorResponse;

public class ConflictRuntimeException extends WebApplicationException {

    public ConflictRuntimeException(String chargeId, String message) {
        super(conflictErrorResponse(format("Operation for charge conflicting, %s, %s", chargeId, message)));
    }

    public ConflictRuntimeException(String chargeId) {
        super(conflictErrorResponse(format("Operation for charge conflicting, %s", chargeId)));
    }

    public ConflictRuntimeException(String message, Exception exception) {
        super(conflictErrorResponse(format("Operation in conflict, %s, %s", message, exception.getMessage())));
    }
}
