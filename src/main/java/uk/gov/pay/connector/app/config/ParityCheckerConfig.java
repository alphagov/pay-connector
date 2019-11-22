package uk.gov.pay.connector.app.config;

import io.dropwizard.Configuration;

public class ParityCheckerConfig extends Configuration {
    
    private long defaultDoNotRetryEmittingEventUntilDurationInSeconds;

    public long getDefaultDoNotRetryEmittingEventUntilDurationInSeconds() {
        return defaultDoNotRetryEmittingEventUntilDurationInSeconds;
    }
}
