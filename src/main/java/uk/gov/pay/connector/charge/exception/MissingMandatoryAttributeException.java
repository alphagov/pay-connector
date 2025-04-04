package uk.gov.pay.connector.charge.exception;

import jakarta.ws.rs.BadRequestException;

import static java.lang.String.format;

public class MissingMandatoryAttributeException extends BadRequestException {
    public MissingMandatoryAttributeException(String fieldName) {
        super(format("Missing mandatory attribute: %s", fieldName));
    }
}
