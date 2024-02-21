package uk.gov.pay.connector.app.config;

import io.dropwizard.core.Configuration;

public class AuthorisationConfig extends Configuration {
    private int asynchronousAuthTimeoutInMilliseconds;
    private int synchronousAuthTimeoutInMilliseconds;

    public int getAsynchronousAuthTimeoutInMilliseconds() {
        return asynchronousAuthTimeoutInMilliseconds;
    }

    public int getSynchronousAuthTimeoutInMilliseconds() {
        return synchronousAuthTimeoutInMilliseconds;
    }
}
