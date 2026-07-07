package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.gatewayaccount.dao.AdyenAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupResponse;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTaskEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;

public class AdyenAccountSetupService {

    private final AdyenAccountSetupDao adyenAccountSetupDao;

    @Inject
    public AdyenAccountSetupService(AdyenAccountSetupDao adyenAccountSetupDao) {
        this.adyenAccountSetupDao = adyenAccountSetupDao;
    }

    @Transactional
    public void completeTestAccountSetup(GatewayAccountEntity gatewayAccountEntity) {
        if (gatewayAccountEntity.isAdyenTestAccount()) {
            var gatewayAccountCredentialsEntity = gatewayAccountEntity.getRecentNonRetiredGatewayAccountCredentialsEntity(ADYEN.getName());
            List.of(AdyenAccountSetupTask.values()).forEach(task ->
                adyenAccountSetupDao.persist(new AdyenAccountSetupTaskEntity(gatewayAccountEntity, task, gatewayAccountCredentialsEntity, AdyenAccountSetupStatus.COMPLETED)));
        } else {
            throw new IllegalArgumentException("Gateway account type must be TEST and gateway name must be ADYEN");
        }
    }

    public AdyenAccountSetupResponse buildResponse(String serviceId, long gatewayAccountId, GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity) {
        return new AdyenAccountSetupResponse(serviceId, 
                gatewayAccountCredentialsEntity.getExternalId(), 
                gatewayAccountId, 
                getTasksWithStatus(gatewayAccountId, gatewayAccountCredentialsEntity.getId()));
    }

    private HashMap<String, Map<String, AdyenAccountSetupStatus>> getTasksWithStatus(long gatewayAccountId, long credentialId) {
        List<AdyenAccountSetupTaskEntity> taskEntities = adyenAccountSetupDao.findByGatewayAccountIdAndCredentialId(gatewayAccountId, credentialId);

        Map<String, AdyenAccountSetupStatus> taskStatusMap = taskEntities.stream()
                .collect(Collectors.toMap(task -> task.getTask().getValue(), AdyenAccountSetupTaskEntity::getStatus));

        var updatedTasks = new HashMap<String, Map<String, AdyenAccountSetupStatus>>();

        Arrays.stream(AdyenAccountSetupTask.values()).forEach(task -> {
            String taskName = task.getValue();

            AdyenAccountSetupStatus status = taskStatusMap.getOrDefault(taskName, AdyenAccountSetupStatus.NOT_STARTED);

            var statusHashMap = new HashMap<String, AdyenAccountSetupStatus>();

            statusHashMap.put("status", status);
            updatedTasks.put(taskName, statusHashMap);
        });
        return updatedTasks;
    }
}
