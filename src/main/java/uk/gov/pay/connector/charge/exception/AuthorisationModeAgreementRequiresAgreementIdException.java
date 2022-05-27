package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class AuthorisationModeAgreementRequiresAgreementIdException extends WebApplicationException {

    public AuthorisationModeAgreementRequiresAgreementIdException(String message) {
        super(badRequestResponse(message));
    }

}
