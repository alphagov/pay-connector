package uk.gov.pay.connector.exception;


import uk.gov.pay.connector.model.ErrorResponse;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.Response.status;

public class ConflictRuntimeException extends ConnectorRuntimeException {

    public ConflictRuntimeException(String message) {
        super(status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflictError(message))
                .type("application/json")
                .build());
        logger.error("Connector Exception: [" + message + "]");
    }

}