package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

public class CardNumberInPaymentLinkReferenceException extends WebApplicationException {
    public CardNumberInPaymentLinkReferenceException() {
        super("Card number entered in a payment link reference");
    }
}
