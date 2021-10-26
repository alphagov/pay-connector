package uk.gov.pay.connector.events.model.payout;

import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;

import java.time.ZonedDateTime;

public class PayoutEvent extends Event {
    private String serviceId;
    private Boolean live;

    public PayoutEvent(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
        this.serviceId = serviceId;
        this.live = live;
    }

    public PayoutEvent(String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.PAYOUT;
    }

    public Boolean isLive() {
        return live;
    }

    public String getServiceId() {
        return serviceId;
    }

    @Override
    public String toString() {
        return "PayoutEvent{" +
                "resourceExternalId=" + getResourceExternalId() +
                ", eventDetails=" + getEventDetails() +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}
