package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class SqsConfig extends Configuration {

    private String endpoint;
    
    @NotNull
    @Valid
    private String captureQueueUrl;
    
    @NotNull
    @Valid
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
