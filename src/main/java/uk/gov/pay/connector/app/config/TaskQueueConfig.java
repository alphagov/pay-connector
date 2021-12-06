package uk.gov.pay.connector.app.config;

import io.dropwizard.Configuration;

public class TaskQueueConfig extends Configuration {

    private Boolean taskQueueEnabled;
    private Boolean collectFeeForStripeFailedPayments;
    private int queueSchedulerNumberOfThreads;
    private int queueSchedulerThreadDelayInSeconds;
    private int failedMessageRetryDelayInSeconds;

    public Boolean getTaskQueueEnabled() {
        return taskQueueEnabled;
    }

    public Boolean getCollectFeeForStripeFailedPayments() {
        return collectFeeForStripeFailedPayments;
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
}
