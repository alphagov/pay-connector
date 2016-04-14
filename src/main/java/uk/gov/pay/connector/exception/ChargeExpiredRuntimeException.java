package uk.gov.pay.connector.exception;

import uk.gov.pay.connector.model.ErrorResponse;

import javax.ws.rs.core.Response;

import static javax.ws.rs.core.Response.status;

public class ChargeExpiredRuntimeException extends ConnectorRuntimeException {
    public ChargeExpiredRuntimeException(String message) {
        super(status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.chargeExpired(message))
                .type("application/json")
                .build());
        logger.error("Connector Exception: [" + message + "]");
    }
}
