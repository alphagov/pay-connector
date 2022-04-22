package uk.gov.pay.connector.charge.exception.motoapi;

import javax.ws.rs.WebApplicationException;

public class CardNumberRejectedException extends WebApplicationException {
    public CardNumberRejectedException(String message) {
        super(message);
    }
}
