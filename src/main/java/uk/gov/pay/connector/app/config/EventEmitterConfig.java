package uk.gov.pay.connector.app.config;

import io.dropwizard.core.Configuration;

public class EventEmitterConfig extends Configuration {
    
    private long defaultDoNotRetryEmittingEventUntilDurationInSeconds;

    public long getDefaultDoNotRetryEmittingEventUntilDurationInSeconds() {
        return defaultDoNotRetryEmittingEventUntilDurationInSeconds;
    }
}
