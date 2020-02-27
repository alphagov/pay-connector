package uk.gov.pay.connector.common.exception;

import uk.gov.pay.commons.model.ErrorIdentifier;

import javax.ws.rs.WebApplicationException;

public class CancelConflictException extends WebApplicationException {

    private final ConflictResult conflictResult;

    public CancelConflictException(String message, ConflictResult conflictResult) {
        super(message);
        this.conflictResult = conflictResult;
    }

    public ConflictResult getConflictResult() {
        return conflictResult;
    }

    public enum ConflictResult {
        CHARGE_FORCIBLY_TRANSITIONED(ErrorIdentifier.CANCEL_CHARGE_FAILURE_DUE_TO_CONFLICTING_TERMINAL_STATE_AT_GATEWAY_CHARGE_STATE_FORCIBLY_TRANSITIONED),
        CHARGE_NOT_TRANSITIONED(ErrorIdentifier.CANCEL_CHARGE_FAILURE_DUE_TO_CONFLICTING_TERMINAL_STATE_AT_GATEWAY_INVALID_STATE_TRANSITION);

        private ErrorIdentifier errorIdentifier;

        ConflictResult(ErrorIdentifier errorIdentifier) {
            this.errorIdentifier = errorIdentifier;
        }

        public ErrorIdentifier getErrorIdentifier() {
            return errorIdentifier;
        }
    }
}
