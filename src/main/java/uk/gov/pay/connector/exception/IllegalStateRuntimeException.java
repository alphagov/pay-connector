package uk.gov.pay.connector.exception;

import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

public class IllegalStateRuntimeException extends ConnectorRuntimeException {
    public IllegalStateRuntimeException(String message) {
        super(serviceErrorResponse(message));
    }
}