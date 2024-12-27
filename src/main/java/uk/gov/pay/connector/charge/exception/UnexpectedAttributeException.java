package uk.gov.pay.connector.charge.exception;

import jakarta.ws.rs.BadRequestException;

import static java.lang.String.format;

public class UnexpectedAttributeException extends BadRequestException {
    public UnexpectedAttributeException(String fieldName) {
        super(format("Unexpected attribute: %s", fieldName));
    }
}
