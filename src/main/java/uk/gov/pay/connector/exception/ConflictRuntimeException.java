package uk.gov.pay.connector.exception;


import static uk.gov.pay.connector.util.ResponseUtil.conflictErrorResponse;

public class ConflictRuntimeException extends ConnectorRuntimeException {

    public ConflictRuntimeException(String message) {
        super(conflictErrorResponse(message));
    }

}