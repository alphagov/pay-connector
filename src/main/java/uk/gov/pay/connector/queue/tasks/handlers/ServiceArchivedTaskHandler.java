package uk.gov.pay.connector.queue.tasks.handlers;

import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.queue.tasks.model.ServiceArchivedTaskData;

import jakarta.inject.Inject;

public class ServiceArchivedTaskHandler {
    private final GatewayAccountService gatewayAccountService;

    @Inject
    public ServiceArchivedTaskHandler(GatewayAccountService gatewayAccountService) {
        this.gatewayAccountService = gatewayAccountService;
    }

    public void process(ServiceArchivedTaskData serviceArchivedTaskData) {
        gatewayAccountService.disableAccountsAndRedactOrDeleteCredentials(serviceArchivedTaskData.getServiceId());
    }
}
