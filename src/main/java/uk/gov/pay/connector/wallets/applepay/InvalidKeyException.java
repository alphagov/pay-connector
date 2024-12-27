package uk.gov.pay.connector.wallets.applepay;

import jakarta.ws.rs.BadRequestException;

public class InvalidKeyException extends BadRequestException {
    public InvalidKeyException(String message) {
        super(message);
    }
}
