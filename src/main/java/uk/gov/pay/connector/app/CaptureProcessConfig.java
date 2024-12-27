package uk.gov.pay.connector.app;


import io.dropwizard.core.Configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class CaptureProcessConfig extends Configuration {

    private int chargesConsideredOverdueForCaptureAfter;
    private int maximumRetries;

    @Valid
    @NotNull
    private Boolean backgroundProcessingEnabled;

    private int failedCaptureRetryDelayInSeconds;
    private int queueSchedulerThreadDelayInSeconds;
    private int queueSchedulerNumberOfThreads;
    private int queueSchedulerShutdownTimeoutInSeconds;

    public int getChargesConsideredOverdueForCaptureAfter() {
        return chargesConsideredOverdueForCaptureAfter;
    }

    public int getMaximumRetries() {
        return maximumRetries;
    }

    public Boolean getBackgroundProcessingEnabled() { return backgroundProcessingEnabled; }

    public int getFailedCaptureRetryDelayInSeconds() {
        return failedCaptureRetryDelayInSeconds;
    }

    public int getQueueSchedulerThreadDelayInSeconds() {
        return queueSchedulerThreadDelayInSeconds;
    }

    public int getQueueSchedulerNumberOfThreads() {
        return queueSchedulerNumberOfThreads;
    }

    public int getQueueSchedulerShutdownTimeoutInSeconds() {
        return queueSchedulerShutdownTimeoutInSeconds;
    }
}
