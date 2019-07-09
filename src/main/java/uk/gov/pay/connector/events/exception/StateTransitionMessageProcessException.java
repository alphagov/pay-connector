package uk.gov.pay.connector.events.exception;

public class StateTransitionMessageProcessException extends Exception {
    public StateTransitionMessageProcessException(Long chargeEventId) {
        super(String.format("Failed to access charge event during state transition message processing [chargeEventId=%d]", chargeEventId));
    }
}
