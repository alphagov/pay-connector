package uk.gov.pay.connector.gatewayaccount.model;

public class StripeAccountSetupUpdateRequest {
    private final StripeAccountSetupTask task;
    private final boolean completed;

    public StripeAccountSetupUpdateRequest(StripeAccountSetupTask task, boolean completed) {
        this.task = task;
        this.completed = completed;
    }

    public StripeAccountSetupTask getTask() {
        return task;
    }

    public boolean isCompleted() {
        return completed;
    }
}
