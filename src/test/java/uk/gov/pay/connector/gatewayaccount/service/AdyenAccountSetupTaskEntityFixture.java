package uk.gov.pay.connector.gatewayaccount.service;

import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTaskEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.ArrayList;
import java.util.Arrays;

public class AdyenAccountSetupTaskEntityFixture {
    
    private GatewayAccountEntity gatewayAccount;
    private GatewayAccountCredentialsEntity gatewayAccountCredential;
    private AdyenAccountSetupTask task;
    private AdyenAccountSetupStatus status;
    
    static AdyenAccountSetupTaskEntityFixture anAdyenAccountSetupTaskEntityFixture() {
        return new AdyenAccountSetupTaskEntityFixture();
    }

    static ArrayList<AdyenAccountSetupTaskEntity> anAdyenAccountSetupTaskEntityListWithAllTasksCompleted(GatewayAccountEntity gatewayAccount, GatewayAccountCredentialsEntity gatewayAccountCredential) {
        var adyenAccountSetupTaskEntities = new ArrayList<AdyenAccountSetupTaskEntity>();
        
        Arrays.stream(AdyenAccountSetupTask.values()).forEach(adyenAccountSetupTask -> adyenAccountSetupTaskEntities
                .add(anAdyenAccountSetupTaskEntityFixture()
                        .withTask(adyenAccountSetupTask)
                        .withCompletedStatus()
                        .withGatewayAccount(gatewayAccount)
                        .withGatewayAccountCredential(gatewayAccountCredential)
                        .build()));
        
        return adyenAccountSetupTaskEntities;
    }
    
    public AdyenAccountSetupTaskEntity build() {
        return new AdyenAccountSetupTaskEntity(gatewayAccount, task, gatewayAccountCredential, status);
    }
    
    public AdyenAccountSetupTaskEntityFixture withGatewayAccount(GatewayAccountEntity gatewayAccount) {
        this.gatewayAccount = gatewayAccount;
        return this;
    }
    
    public AdyenAccountSetupTaskEntityFixture withGatewayAccountCredential(GatewayAccountCredentialsEntity gatewayAccountCredential) {
        this.gatewayAccountCredential = gatewayAccountCredential;
        return this;
    }
    
    public AdyenAccountSetupTaskEntityFixture withTask(AdyenAccountSetupTask task) {
        this.task = task;
        return this;
    }

    public AdyenAccountSetupTaskEntityFixture withCompletedStatus() {
        this.status = AdyenAccountSetupStatus.COMPLETED;
        return this;
    }
}
