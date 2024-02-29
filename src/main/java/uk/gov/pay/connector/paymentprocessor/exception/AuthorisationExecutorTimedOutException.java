package uk.gov.pay.connector.paymentprocessor.exception;

public class AuthorisationExecutorTimedOutException extends Exception {
    public AuthorisationExecutorTimedOutException(String message) {
        super(message);
    }
}
