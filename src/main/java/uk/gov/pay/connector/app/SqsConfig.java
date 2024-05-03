package uk.gov.pay.connector.app;

import io.dropwizard.core.Configuration;
import uk.gov.pay.connector.app.validator.ValidSqsConfig;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;

@ValidSqsConfig
public class SqsConfig extends Configuration {

    @NotNull
    private boolean nonStandardServiceEndpoint;
    private String endpoint;
    @NotNull
    private String captureQueueUrl;
    private String payoutReconcileQueueUrl;
    @NotNull
    private String region;
    private String accessKey;
    private String secretKey;

    @Max(20)
    private int messageMaximumWaitTimeInSeconds;
    @Max(10)
    private int messageMaximumBatchSize;

    @Max(900)
    private int maxAllowedDeliveryDelayInSeconds;

    private String eventQueueUrl;

    private String taskQueueUrl;

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

    public int getMessageMaximumWaitTimeInSeconds() {
        return messageMaximumWaitTimeInSeconds;
    }

    public int getMessageMaximumBatchSize() {
        return messageMaximumBatchSize;
    }

    public int getMaxAllowedDeliveryDelayInSeconds() {
        return maxAllowedDeliveryDelayInSeconds;
    }

    public String getEventQueueUrl() {
        return eventQueueUrl;
    }

    public String getPayoutReconcileQueueUrl() {
        return payoutReconcileQueueUrl;
    }

    public String getTaskQueueUrl() {
        return taskQueueUrl;
    }
}
