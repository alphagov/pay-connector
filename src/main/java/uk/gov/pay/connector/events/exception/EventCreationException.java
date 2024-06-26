package uk.gov.pay.connector.events.exception;

public class EventCreationException extends Exception {
    public EventCreationException(String id, String message) {
        super(String.format("Event id = [%s], exception = %s", id, message));
    }
}
