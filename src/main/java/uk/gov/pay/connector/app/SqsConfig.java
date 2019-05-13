package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

public class SqsConfig extends Configuration {

    private String endpoint;
    private String captureQueueUrl;
    private String region;

    public String getEndpoint() {
        return endpoint;
    }

    public String getCaptureQueueUrl() {
        return captureQueueUrl;
    }

    public String getRegion() {
        return region;
    }
}
