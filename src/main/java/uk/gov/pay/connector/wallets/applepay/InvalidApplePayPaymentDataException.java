package uk.gov.pay.connector.wallets.applepay;

import javax.ws.rs.BadRequestException;

public class InvalidApplePayPaymentDataException extends BadRequestException {
    public InvalidApplePayPaymentDataException(String message) {
        super(message);
    }
}
