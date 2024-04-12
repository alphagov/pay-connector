package uk.gov.pay.connector.app.config;

import io.dropwizard.Configuration;

public class Authorisation3dsConfig extends Configuration {

    private int maximumNumberOfTimesToAllowUserToAttempt3ds;

    public int getMaximumNumberOfTimesToAllowUserToAttempt3ds() {
        return maximumNumberOfTimesToAllowUserToAttempt3ds;
    }

}
