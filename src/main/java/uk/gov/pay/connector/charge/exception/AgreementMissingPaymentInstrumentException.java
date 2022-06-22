package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

public class AgreementMissingPaymentInstrumentException extends WebApplicationException {

    public AgreementMissingPaymentInstrumentException(String message) {
        super(message);
    }

}
