package uk.gov.pay.connector.charge.exception.motoapi;

import jakarta.ws.rs.WebApplicationException;

public class CardNumberRejectedException extends WebApplicationException {
    public CardNumberRejectedException(String message) {
        super(message);
    }
}
