package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

public final class CardNumberInPaymentLinkReferenceException extends WebApplicationException implements ErrorListMapper.Error {
    public CardNumberInPaymentLinkReferenceException() {
        super("Card number entered in a payment link reference");
    }
}
