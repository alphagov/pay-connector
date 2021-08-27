package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;

import java.time.ZonedDateTime;

public abstract class RefundEvent extends Event {
    private String serviceId;
    private Boolean live;
    private String parentResourceExternalId;

    public RefundEvent(String serviceId, Boolean live, String resourceExternalId, String parentResourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
        this.parentResourceExternalId = parentResourceExternalId;
        this.serviceId = serviceId;
        this.live = live;
    }

    public RefundEvent(String serviceId, Boolean live, String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
        this.serviceId = serviceId;
        this.live = live;
    }

    public RefundEvent(String serviceId, Boolean live, String resourceExternalId, String parentResourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
        this.parentResourceExternalId = parentResourceExternalId;
        this.serviceId = serviceId;
        this.live = live;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.REFUND;
    }

    public String getParentResourceExternalId() {
        return parentResourceExternalId;
    }
}
