package uk.gov.pay.connector.events.exception;

public class StateTransitionMessageProcessException extends Exception {
    public StateTransitionMessageProcessException(String id) {
        super(String.format("Failed to handle event during state transition message processing [eventId=%s]", id));
    }
}
