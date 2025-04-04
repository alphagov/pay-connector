package uk.gov.pay.connector.charge.exception;

import jakarta.ws.rs.BadRequestException;

import static java.lang.String.format;

public class InvalidAttributeValueException extends BadRequestException {
    public InvalidAttributeValueException(String fieldName, String message) {
        super(format("Invalid attribute value: %s. %s", fieldName, message));
    }
}
