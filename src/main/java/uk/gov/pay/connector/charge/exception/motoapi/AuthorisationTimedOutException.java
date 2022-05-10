package uk.gov.pay.connector.charge.exception.motoapi;

import javax.ws.rs.WebApplicationException;

public class AuthorisationTimedOutException extends WebApplicationException {
    public AuthorisationTimedOutException() {
        super("Authorising the payment timed out");
    }
}
