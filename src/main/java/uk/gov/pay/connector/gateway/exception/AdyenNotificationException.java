package uk.gov.pay.connector.gateway.exception;

public class AdyenNotificationException extends RuntimeException{
    public AdyenNotificationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AdyenNotificationException(String message) {
        super(message);
    }
}
