package uk.gov.pay.connector.charge.exception;

import jakarta.ws.rs.WebApplicationException;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

public class ChargeException extends WebApplicationException {

    private final ErrorIdentifier errorIdentifier;
    private final int httpResponseStatusCode;

    public ChargeException(String message, ErrorIdentifier errorIdentifier, int httpResponseStatusCode) {
        super(message);
        this.errorIdentifier = errorIdentifier;
        this.httpResponseStatusCode = httpResponseStatusCode;
    }

    public ErrorIdentifier getErrorIdentifier() {
        return errorIdentifier;
    }

    public int getHttpResponseStatusCode() {
        return httpResponseStatusCode;
    }
}
