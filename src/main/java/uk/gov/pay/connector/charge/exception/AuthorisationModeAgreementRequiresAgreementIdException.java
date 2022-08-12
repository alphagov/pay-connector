package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

public class AuthorisationModeAgreementRequiresAgreementIdException extends WebApplicationException {

    public AuthorisationModeAgreementRequiresAgreementIdException(String message) {
        super(message);
    }

}
