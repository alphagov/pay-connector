package uk.gov.pay.connector.events.model.payout;

import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;

import java.time.Instant;

public class PayoutEvent extends Event {
    private String serviceId;
    private Boolean live;

    public PayoutEvent(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(timestamp, resourceExternalId, eventDetails);
        this.serviceId = serviceId;
        this.live = live;
    }

    public PayoutEvent(String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(timestamp, resourceExternalId, eventDetails);
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
}
