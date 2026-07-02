package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.Inject;
import uk.gov.pay.connector.gatewayaccount.dao.AdyenAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupResponse;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTaskEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class AydenAccountSetupService {

    private final AdyenAccountSetupDao adyenAccountSetupDao;

    @Inject
    public AydenAccountSetupService(AdyenAccountSetupDao adyenAccountSetupDao) {
        this.adyenAccountSetupDao = adyenAccountSetupDao;
    }

    public AdyenAccountSetupResponse buildResponse(String serviceId, long gatewayAccountId, GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity) {
        AdyenAccountSetupResponse adyenAccountSetupResponse = new AdyenAccountSetupResponse();

        adyenAccountSetupResponse.setServiceId(serviceId);
        adyenAccountSetupResponse.setGatewayAccountId(gatewayAccountId);
        adyenAccountSetupResponse.setCredentialExternalId(gatewayAccountCredentialsEntity.getExternalId());

        adyenAccountSetupResponse.setTasks(getTasksWithStatus(gatewayAccountId, gatewayAccountCredentialsEntity.getId()));

        return adyenAccountSetupResponse;
    }

    private HashMap<String, Map<String, AdyenAccountSetupStatus>> getTasksWithStatus(long gatewayAccountId, Long gatewayAccountCredentialsId) {
        List<AdyenAccountSetupTaskEntity> taskEntities = adyenAccountSetupDao.findByGatewayAccountIdAndCredentialId(gatewayAccountId, gatewayAccountCredentialsId);

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
