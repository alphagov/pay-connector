package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

public class ConflictWebApplicationException extends WebApplicationException {

    public ConflictWebApplicationException(String message) {
        super(message);
    }
}
