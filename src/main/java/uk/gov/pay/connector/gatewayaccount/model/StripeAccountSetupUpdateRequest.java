package uk.gov.pay.connector.gatewayaccount.model;

import uk.gov.service.payments.commons.model.jsonpatch.JsonPatchRequest;

public class StripeAccountSetupUpdateRequest {
    private final StripeAccountSetupTask task;
    private final boolean completed;

    public StripeAccountSetupUpdateRequest(StripeAccountSetupTask task, boolean completed) {
        this.task = task;
        this.completed = completed;
    }
    
    public static StripeAccountSetupUpdateRequest from(StripeSetupPatchRequest patchRequest) {
        StripeAccountSetupTask task = StripeAccountSetupTask.valueOf(patchRequest.getPath().toUpperCase());
        return new StripeAccountSetupUpdateRequest(task, Boolean.parseBoolean(patchRequest.getValue()));
    }
    
    public StripeAccountSetupTask getTask() {
        return task;
    }

    public boolean isCompleted() {
        return completed;
    }
}
