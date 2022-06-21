package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

public class PaymentInstrumentNotActiveException extends WebApplicationException {

    public PaymentInstrumentNotActiveException(String message) {
        super(message);
    }

}
