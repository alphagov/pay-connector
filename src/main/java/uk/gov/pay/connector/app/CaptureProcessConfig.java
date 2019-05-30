package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;
import io.dropwizard.util.Duration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class CaptureProcessConfig extends Configuration {
    private long schedulerInitialDelayInSeconds;
    private long schedulerRandomIntervalMinimumInSeconds;
    private long schedulerRandomIntervalMaximumInSeconds;
    private int schedulerThreads;

    private int batchSize;
    private Duration retryFailuresEvery;
    private int maximumRetries;

    @Valid
    @NotNull
    private Boolean captureUsingSQS;

    @Valid
    @NotNull
    private Boolean backgroundProcessingEnabled;

    private int failedCaptureRetryDelayInSeconds;
    private int queueSchedulerThreadDelayInSeconds;
    private int queueSchedulerNumberOfThreads;

    public long getSchedulerInitialDelayInSeconds() {
        return schedulerInitialDelayInSeconds;
    }

    public long getSchedulerRandomIntervalMinimumInSeconds() {
        return schedulerRandomIntervalMinimumInSeconds;
    }

    public long getSchedulerRandomIntervalMaximumInSeconds() {
        return schedulerRandomIntervalMaximumInSeconds;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public Duration getRetryFailuresEvery() {
        return retryFailuresEvery;
    }

    public java.time.Duration getRetryFailuresEveryAsJavaDuration() {
        return java.time.Duration.ofMillis(retryFailuresEvery.toMilliseconds());
    }

    public int getMaximumRetries() {
        return maximumRetries;
    }

    public int getSchedulerThreads() {
        return schedulerThreads;
    }

    public Boolean getCaptureUsingSQS() {
        return captureUsingSQS;
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
}
