package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

public class AgreementIdWithIncompatibleOtherOptionsException extends WebApplicationException {

    public AgreementIdWithIncompatibleOtherOptionsException(String message) {
        super(message);
    }

}
