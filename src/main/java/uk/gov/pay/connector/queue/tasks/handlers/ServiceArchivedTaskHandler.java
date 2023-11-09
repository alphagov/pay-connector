package uk.gov.pay.connector.queue.tasks.handlers;

import io.prometheus.client.Histogram;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.queue.tasks.model.ServiceArchivedTaskData;

public class ServiceArchivedTaskHandler {
    private final GatewayAccountService gatewayAccountService;

    private static final Histogram duration = Histogram.build()
            .name("disable_gateway_accounts_and_credentials_job_duration_seconds")
            .help("Duration of disabling gateway accounts and deleting/redacting credentials job in seconds")
            .unit("seconds")
            .register();

    public ServiceArchivedTaskHandler(GatewayAccountService gatewayAccountService) {
        this.gatewayAccountService = gatewayAccountService;
    }

    public void process(ServiceArchivedTaskData serviceArchivedTaskData) {
        Histogram.Timer responseTimeTimer = duration.startTimer();
        try {
            gatewayAccountService.disableAccountsAndRedactOrDeleteCredentials(serviceArchivedTaskData.getServiceId());
        } finally {
            responseTimeTimer.observeDuration();
        }
    }
}
