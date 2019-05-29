package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

public class SqsConfig extends Configuration {

    private boolean nonStandardServiceEndpoint;
    private String endpoint;
    private String captureQueueUrl;
    private String region;
    private String accessKey;
    private String secretKey;

    public String getEndpoint() {
        return endpoint;
    }

    public String getCaptureQueueUrl() {
        return captureQueueUrl;
    }

    public String getRegion() {
        return region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public boolean isNonStandardServiceEndpoint() {
        return nonStandardServiceEndpoint;
    }
}
