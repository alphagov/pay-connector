package uk.gov.pay.connector.events.model;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.Instant;

public class UnspecifiedEvent extends Event {

    public UnspecifiedEvent() {
        super((Instant) null, null);
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
    public Instant getTimestamp() {
        return null;
    }
}
