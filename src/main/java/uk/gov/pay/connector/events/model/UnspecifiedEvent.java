package uk.gov.pay.connector.events.model;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

public class UnspecifiedEvent extends Event {

    public UnspecifiedEvent() {
        super(null, null);
    }

    public UnspecifiedEvent(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }

    @Override
    public ResourceType getResourceType() {
        return null;
    }

    @Override
    public String getResourceExternalId() {
        return null;
    }

    @Override
    public String getEventType() {
        return null;
    }

    @Override
    public EventDetails getEventDetails() {
        return null;
    }

    @Override
    public ZonedDateTime getTimestamp() {
        return null;
    }
}
