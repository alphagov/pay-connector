package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;

import java.time.ZonedDateTime;

public abstract class RefundEvent extends Event {
    private String serviceId;
    private Boolean live;
    private String parentResourceExternalId;

    public RefundEvent(String serviceId, boolean live, String resourceExternalId, String parentResourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
        this.parentResourceExternalId = parentResourceExternalId;
        this.serviceId = serviceId;
        this.live = live;
    }
    
    public RefundEvent(String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.REFUND;
    }

    public String getParentResourceExternalId() {
        return parentResourceExternalId;
    }

    public Boolean isLive() {
        return live;
    }

    public String getServiceId() {
        return serviceId;
    }
}
