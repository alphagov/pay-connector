package uk.gov.pay.connector.gatewayaccount.model;

public class StripeAccountSetupUpdateRequest {
    private final StripeAccountSetupTask task;
    private final boolean completed;

    public StripeAccountSetupUpdateRequest(StripeAccountSetupTask task, boolean completed) {
        this.task = task;
        this.completed = completed;
    }
    
    public static StripeAccountSetupUpdateRequest from(AccountSetupPatchRequest patchRequest) {
        StripeAccountSetupTask task = StripeAccountSetupTask.valueOf(patchRequest.path().toUpperCase());
        return new StripeAccountSetupUpdateRequest(task, Boolean.parseBoolean(patchRequest.value()));
    }
    
    public StripeAccountSetupTask getTask() {
        return task;
    }

    public boolean isCompleted() {
        return completed;
    }
}
