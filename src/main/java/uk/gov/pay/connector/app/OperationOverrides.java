package uk.gov.pay.connector.app;

import io.dropwizard.util.Duration;

public class OperationOverrides {
    private Duration readTimeout;

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
