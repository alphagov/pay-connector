package uk.gov.pay.connector.charge.exception.motoapi;

import jakarta.ws.rs.WebApplicationException;

public class AuthorisationErrorException extends WebApplicationException {
    public AuthorisationErrorException() {
        super("There was an error authorising the payment");
    }
}
