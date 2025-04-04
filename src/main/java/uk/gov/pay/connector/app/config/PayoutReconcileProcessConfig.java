package uk.gov.pay.connector.app.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PayoutReconcileProcessConfig {

    @NotNull
    private Boolean payoutReconcileQueueEnabled;
    @Min(1)
    private int failedPayoutReconcileMessageRetryDelayInSeconds;
    @Min(1)
    private int queueSchedulerThreadDelayInSeconds;
    @Min(1)
    private int queueSchedulerNumberOfThreads;

    public Boolean getPayoutReconcileQueueEnabled() {
        return payoutReconcileQueueEnabled;
    }

    public int getFailedPayoutReconcileMessageRetryDelayInSeconds() {
        return failedPayoutReconcileMessageRetryDelayInSeconds;
    }

    public int getQueueSchedulerThreadDelayInSeconds() {
        return queueSchedulerThreadDelayInSeconds;
    }

    public int getQueueSchedulerNumberOfThreads() {
        return queueSchedulerNumberOfThreads;
    }
}
