package uk.gov.pay.connector.exception;

import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class ChargeNotFoundRuntimeException extends ConnectorRuntimeException {
    public ChargeNotFoundRuntimeException(String message) {
        super(notFoundResponse(message));
    }
}
