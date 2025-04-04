package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;

public class GatewayClientFactory {

    private final ClientFactory clientFactory;

    @Inject
    public GatewayClientFactory(ClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public GatewayClient createGatewayClient(PaymentGatewayName gateway,
                                             GatewayOperation operation,
                                             MetricRegistry metricRegistry) {
        Client client = clientFactory.createWithDropwizardClient(gateway, operation, metricRegistry);
        return new GatewayClient(client, metricRegistry);
    }

    public GatewayClient createGatewayClient(PaymentGatewayName gateway,
                                             MetricRegistry metricRegistry) {
        Client client = clientFactory.createWithDropwizardClient(gateway, metricRegistry);
        return new GatewayClient(client, metricRegistry);
    }
}
