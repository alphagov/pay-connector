package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.gatewayaccount.dao.AdyenAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTaskEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.List;

public class AdyenAccountSetupService {

    private final AdyenAccountSetupDao aydenAccountSetupDao;

    @Inject
    public AdyenAccountSetupService(AdyenAccountSetupDao aydenAccountSetupDao) {
        this.aydenAccountSetupDao = aydenAccountSetupDao;
    }

    @Transactional
    public void completeTestAccountSetup(GatewayAccountEntity gatewayAccountEntity) {
        if (gatewayAccountEntity.isAdyenTestAccount()) {
            List.of(AdyenAccountSetupTask.values()).forEach(task -> {
                aydenAccountSetupDao.persist(new AdyenAccountSetupTaskEntity(gatewayAccountEntity, task, AdyenAccountSetupStatus.COMPLETED));
            });
        } else {
            throw new IllegalArgumentException("Gateway account type must be TEST and gateway name must be ADYEN");
        }
    }
}
