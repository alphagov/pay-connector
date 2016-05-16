package uk.gov.pay.connector.exception;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
