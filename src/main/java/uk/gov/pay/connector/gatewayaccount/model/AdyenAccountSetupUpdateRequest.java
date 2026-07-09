package uk.gov.pay.connector.gatewayaccount.model;

public record AdyenAccountSetupUpdateRequest(
        AdyenAccountSetupTask task, 
        AdyenAccountSetupStatus status) {
    
    public static AdyenAccountSetupUpdateRequest from(AccountSetupPatchRequest request) {
        AdyenAccountSetupTask setupTask = AdyenAccountSetupTask.valueOf(request.path().toUpperCase());
        AdyenAccountSetupStatus setupStatus = AdyenAccountSetupStatus.valueOf(request.value().toUpperCase());
        return new AdyenAccountSetupUpdateRequest(setupTask, setupStatus);
    }
}

