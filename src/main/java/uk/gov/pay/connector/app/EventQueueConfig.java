package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

public class EventQueueConfig extends Configuration {
    private Boolean eventQueueEnabled;

    public Boolean getEventQueueEnabled() {
        return eventQueueEnabled;
    }
}
