package uk.gov.pay.connector.gatewayaccount.service;

import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetup;


public class AydenAccountSetupService {

    public AdyenAccountSetup getCompletedTasks(String serviceId, long gatewayAccountId, String credentialExternalId) {
        AdyenAccountSetup adyenAccountSetup = new AdyenAccountSetup();
        
        adyenAccountSetup.setServiceId(serviceId);
        adyenAccountSetup.setGatewayAccountId(gatewayAccountId);
        adyenAccountSetup.setCredentialExternalId(credentialExternalId);
        
        return adyenAccountSetup;
    }
}
