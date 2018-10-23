package uk.gov.pay.connector.common.exception;

import static java.lang.String.format;

public class InvalidStateTransitionException extends IllegalStateException {

    public InvalidStateTransitionException(String currentState, String targetState) {
        super(format("Charge state transition [%s] -> [%s] not allowed", currentState, targetState));
    }
}
