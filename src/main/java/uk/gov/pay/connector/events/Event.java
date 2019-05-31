package uk.gov.pay.connector.events;

public abstract class Event {

    public abstract ResourceType getResourceType();
    
    public abstract String getEventType();
}
