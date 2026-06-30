package uk.gov.pay.connector.gatewayaccount.service;

import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupResponse;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus.NOT_STARTED;

public class AydenAccountSetupService {

    public AdyenAccountSetupResponse getCompletedTasks(String serviceId, long gatewayAccountId, String credentialExternalId) {
        AdyenAccountSetupResponse adyenAccountSetupResponse = new AdyenAccountSetupResponse();
        
        adyenAccountSetupResponse.setServiceId(serviceId);
        adyenAccountSetupResponse.setGatewayAccountId(gatewayAccountId);
        adyenAccountSetupResponse.setCredentialExternalId(credentialExternalId);
        adyenAccountSetupResponse.setTasks(populateDefaultTasksStatus());
        
        return adyenAccountSetupResponse;
    }

    private HashMap<String, Map<String, AdyenAccountSetupStatus>> populateDefaultTasksStatus() {
        var setupTasks = new HashMap<String, Map<String, AdyenAccountSetupStatus>>();
        Arrays.stream(AdyenAccountSetupTask.values()).forEach(task ->
                setupTasks.put(task.getValue(),
                        Map.of("status", NOT_STARTED)));
        return setupTasks;
    }
}
