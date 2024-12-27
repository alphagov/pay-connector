package uk.gov.pay.connector.wallets.applepay;

import jakarta.ws.rs.BadRequestException;

public class InvalidApplePayPaymentDataException extends BadRequestException {
    public InvalidApplePayPaymentDataException(String message) {
        super(message);
    }
}
