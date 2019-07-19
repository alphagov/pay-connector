package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.EmptyEventDetails;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;

import java.time.ZonedDateTime;
import java.util.Objects;

public class PaymentEvent extends Event {
    private String resourceExternalId;
    private EventDetails eventDetails;
    private ZonedDateTime timestamp;

    public PaymentEvent(String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        this.resourceExternalId = resourceExternalId;
        this.eventDetails = eventDetails;
        this.timestamp = timestamp;
    }

    public PaymentEvent(String resourceExternalId, ZonedDateTime timestamp) {
        this.resourceExternalId = resourceExternalId;
        this.timestamp = timestamp;
        this.eventDetails = new EmptyEventDetails();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentEvent that = (PaymentEvent) o;
        return Objects.equals(resourceExternalId, that.resourceExternalId) &&
                Objects.equals(eventDetails, that.eventDetails) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceExternalId, eventDetails, timestamp);
    }

    @Override
    public String toString() {
        return "PaymentEvent{" +
                "resourceExternalId='" + resourceExternalId + '\'' +
                ", eventDetails=" + eventDetails +
                ", timestamp=" + timestamp +
                '}';
    }
}
