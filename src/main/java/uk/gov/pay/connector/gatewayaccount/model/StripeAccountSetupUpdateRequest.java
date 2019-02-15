package uk.gov.pay.connector.gatewayaccount.model;

import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchRequest;

public class StripeAccountSetupUpdateRequest {
    private final StripeAccountSetupTask task;
    private final boolean completed;

    public StripeAccountSetupUpdateRequest(StripeAccountSetupTask task, boolean completed) {
        this.task = task;
        this.completed = completed;
    }

    public static StripeAccountSetupUpdateRequest from(JsonPatchRequest jsonPatchRequest) {
        StripeAccountSetupTask task = StripeAccountSetupTask.valueOf(jsonPatchRequest.getPath().toUpperCase());
        return new StripeAccountSetupUpdateRequest(task, jsonPatchRequest.valueAsBoolean());
    }
    
    public StripeAccountSetupTask getTask() {
        return task;
    }

    public boolean isCompleted() {
        return completed;
    }
}
