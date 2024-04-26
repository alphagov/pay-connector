package uk.gov.pay.connector.app.config;

import io.dropwizard.Configuration;

public class TaskQueueConfig extends Configuration {

    private Boolean taskQueueEnabled;
    private int queueSchedulerNumberOfThreads;
    private int queueSchedulerThreadDelayInSeconds;
    private int failedMessageRetryDelayInSeconds;
    private int queueSchedulerShutdownTimeoutInSeconds;
    private int deliveryDelayInSeconds;

    public Boolean getTaskQueueEnabled() {
        return taskQueueEnabled;
    }

    public int getQueueSchedulerNumberOfThreads() {
        return queueSchedulerNumberOfThreads;
    }

    public int getQueueSchedulerThreadDelayInSeconds() {
        return queueSchedulerThreadDelayInSeconds;
    }

    public int getFailedMessageRetryDelayInSeconds() {
        return failedMessageRetryDelayInSeconds;
    }

    public int getQueueSchedulerShutdownTimeoutInSeconds() {
        return queueSchedulerShutdownTimeoutInSeconds;
    }

    public int getDeliveryDelayInSeconds() {
        return deliveryDelayInSeconds;
    }
}
