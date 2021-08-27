package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;

import java.time.ZonedDateTime;

public class PaymentEvent extends Event {
    private String serviceId;
    private Boolean live;
    
    public PaymentEvent(String serviceId, Boolean live, String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
        this.serviceId = serviceId;
        this.live = live;
    }

    public PaymentEvent(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
        this.serviceId = serviceId;
        this.live = live;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.PAYMENT;
    }
    
    @Override
    public String toString() {
        return "PaymentEvent{" +
                "resourceExternalId='" + getResourceExternalId() + '\'' +
                ", eventDetails=" + getEventDetails() +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}
