package uk.gov.pay.connector.app.config;

import io.dropwizard.Configuration;

public class AuthorisationConfig extends Configuration {
    private int asynchronousAuthTimeoutInSeconds;
    private int synchronousAuthTimeoutInMilliseconds;

    public int getAsynchronousAuthTimeoutInSeconds() {
        return asynchronousAuthTimeoutInSeconds;
    }

    public int getSynchronousAuthTimeoutInMilliseconds() {
        return synchronousAuthTimeoutInMilliseconds;
    }
}
