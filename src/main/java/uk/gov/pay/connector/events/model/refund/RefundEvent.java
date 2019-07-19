package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;

import java.time.ZonedDateTime;

public abstract class RefundEvent extends Event {

    private String parentResourceExternalId;

    public RefundEvent(String resourceExternalId, String parentResourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
        this.parentResourceExternalId = parentResourceExternalId;
    }

    public RefundEvent(String resourceExternalId, String parentResourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
        this.parentResourceExternalId = parentResourceExternalId;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.REFUND;
    }

    public String getParentResourceExternalId() {
        return parentResourceExternalId;
    }
}
