package uk.gov.pay.connector.events;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

public class PaymentEvent extends Event {
    private String resourceExternalId;
    private EventDetails eventDetails;
    private ZonedDateTime timestamp;

    public PaymentEvent(String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        this.resourceExternalId = resourceExternalId;
        this.eventDetails = eventDetails;
        this.timestamp = timestamp;
    }

    @Override
    public String getResourceExternalId() {
        return resourceExternalId;
    }

    @Override
    public String getEventType() {
        return eventTypeForClass(this.getClass());
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

    static String eventTypeForClass(Class clazz) {
        return clazz.getSimpleName().replaceAll("([^A-Z]+)([A-Z])", "$1_$2").toUpperCase();
    }
}
