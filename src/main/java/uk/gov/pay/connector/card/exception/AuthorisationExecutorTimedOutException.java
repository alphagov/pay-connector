package uk.gov.pay.connector.card.exception;

public class AuthorisationExecutorTimedOutException extends Exception {
    public AuthorisationExecutorTimedOutException(String message) {
        super(message);
    }
}
