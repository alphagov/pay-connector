package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;
import io.dropwizard.util.Duration;

public class CaptureProcessConfig extends Configuration {
    private int batchSize;
    private Duration retryFailuresEvery;

    public int getBatchSize() {
        return batchSize;
    }

    public Duration getRetryFailuresEvery() {
        return retryFailuresEvery;
    }

    public java.time.Duration getRetryFailuresEveryAsJavaDuration() {
        return java.time.Duration.ofMillis(retryFailuresEvery.toMilliseconds());
    }
}
