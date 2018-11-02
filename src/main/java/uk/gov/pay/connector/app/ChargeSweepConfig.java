package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

public class ChargeSweepConfig extends Configuration {

    private int defaultChargeExpiryThreshold;
    private int awaitingCaptureExpiryThreshold;

    public int getDefaultChargeExpiryThreshold() {
        return defaultChargeExpiryThreshold;
    }

    public int getAwaitingCaptureExpiryThreshold() {
        return awaitingCaptureExpiryThreshold;
    }
}
