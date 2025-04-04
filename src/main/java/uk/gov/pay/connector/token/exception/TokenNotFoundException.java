package uk.gov.pay.connector.token.exception;

import jakarta.ws.rs.WebApplicationException;

public class TokenNotFoundException extends WebApplicationException {

    public TokenNotFoundException(String message) {
        super(message);
    }

}
