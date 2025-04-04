package uk.gov.pay.connector.charge.exception;

import jakarta.ws.rs.WebApplicationException;

public class ConflictWebApplicationException extends WebApplicationException {

    public ConflictWebApplicationException(String message) {
        super(message);
    }
}
