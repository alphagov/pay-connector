package uk.gov.pay.connector.events;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

public class PaymentEvent extends Event {
    private String resourceExternalId;
    private String eventType;
    private EventDetails eventDetails;
    private ZonedDateTime timestamp;

    public PaymentEvent() { }
    
    public PaymentEvent(String resourceExternalId, String eventType, EventDetails eventDetails, ZonedDateTime timestamp) {
        this.resourceExternalId = resourceExternalId;
        this.eventType = eventType;
        this.eventDetails = eventDetails;
        this.timestamp = timestamp;
    }

    @Override
    public String getResourceExternalId() {
        return resourceExternalId;
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public EventDetails getEventDetails() {
        return eventDetails;
    }

    @Override
    public ZonedDateTime getTimestamp() {
        return timestamp;
    }
    
    @Override
    public ResourceType getResourceType() {
        return ResourceType.PAYMENT;
    }
}
