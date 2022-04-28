package uk.gov.pay.connector.charge.exception.motoapi;

import javax.ws.rs.WebApplicationException;

public class AuthorisationRejectedException extends WebApplicationException {
    public AuthorisationRejectedException() {
        super("The payment was rejected");
    }
}
