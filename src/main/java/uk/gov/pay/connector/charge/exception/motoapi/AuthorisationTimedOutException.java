package uk.gov.pay.connector.charge.exception.motoapi;

import jakarta.ws.rs.WebApplicationException;

public class AuthorisationTimedOutException extends WebApplicationException {
    public AuthorisationTimedOutException() {
        super("Authorising the payment timed out");
    }
}
