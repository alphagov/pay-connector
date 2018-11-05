package uk.gov.pay.connector.applepay;

import javax.ws.rs.BadRequestException;

public class InvalidKeyException extends BadRequestException {
    public InvalidKeyException(String message) {
        super(message);
    }
}
