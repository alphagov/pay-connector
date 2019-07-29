package uk.gov.pay.connector.events.exception;

public class EventCreationException extends Exception {
    public EventCreationException(String id) {
        super(String.format("Failed to handle event during state transition message processing [eventId=%s]", id));
    }
}
