package uk.gov.pay.connector.common.exception;

import uk.gov.pay.connector.events.Event;

import static java.lang.String.format;

public class InvalidStateTransitionException extends IllegalStateException {

    public InvalidStateTransitionException(String currentState, String targetState, Event event) {
        super(format("Charge state transition [%s] -> [%s] not allowed [event={}]", currentState, targetState, event));
    }
}
