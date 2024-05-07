package uk.gov.pay.connector.app.config;

import io.dropwizard.Configuration;

public class EmittedEventSweepConfig extends Configuration {
    
    private int notEmittedEventMaxAgeInSeconds;

    public int getNotEmittedEventMaxAgeInSeconds() {
        return notEmittedEventMaxAgeInSeconds;
    }
}
