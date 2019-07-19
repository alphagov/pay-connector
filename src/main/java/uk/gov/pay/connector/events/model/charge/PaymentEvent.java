package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;

import java.time.ZonedDateTime;

public class PaymentEvent extends Event {


    public PaymentEvent(String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    public PaymentEvent(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
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
