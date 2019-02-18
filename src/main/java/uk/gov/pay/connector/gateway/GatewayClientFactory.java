package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClient;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import java.util.Map;

public class GatewayClientFactory {

    private final ClientFactory clientFactory;

    @Inject
    public GatewayClientFactory(ClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public StripeGatewayClient createStripeGatewayClient(PaymentGatewayName gateway,
                                                         MetricRegistry metricRegistry) {
        Client client = clientFactory.createWithDropwizardClient(gateway, metricRegistry);
        return new StripeGatewayClient(client, metricRegistry);
    }

    public GatewayClient createGatewayClient(PaymentGatewayName gateway,
                                             GatewayOperation operation,
                                             Map<String, String> gatewayUrlMap,
                                             MetricRegistry metricRegistry) {
        Client client = clientFactory.createWithDropwizardClient(gateway, operation, metricRegistry);
        return new GatewayClient(client, gatewayUrlMap, metricRegistry);
    }

    public GatewayClient createGatewayClient(PaymentGatewayName gateway,
                                             Map<String, String> gatewayUrlMap,
                                             MetricRegistry metricRegistry) {
        Client client = clientFactory.createWithDropwizardClient(gateway, metricRegistry);
        return new GatewayClient(client, gatewayUrlMap, metricRegistry);
    }
}
