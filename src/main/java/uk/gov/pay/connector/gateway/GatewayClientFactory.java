package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClient;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation.Builder;
import java.util.Map;
import java.util.function.BiFunction;

public class GatewayClientFactory {

    final ClientFactory clientFactory;

    @Inject
    public GatewayClientFactory(ClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public StripeGatewayClient createStripeGatewayClient(PaymentGatewayName gateway,
                                                         GatewayOperation operation,
                                                         MetricRegistry metricRegistry,
                                                         StripeGatewayConfig stripeGatewayConfig) {
        Client client = clientFactory.createWithDropwizardClient(gateway, operation, metricRegistry);
        return new StripeGatewayClient(client, metricRegistry, stripeGatewayConfig);
    }
    
    public GatewayClient createGatewayClient(PaymentGatewayName gateway, 
                                             GatewayOperation operation,
                                             Map<String, String> gatewayUrlMap, 
                                             BiFunction<GatewayOrder, Builder, Builder> sessionIdentifier,
                                             MetricRegistry metricRegistry)
    {
        Client client = clientFactory.createWithDropwizardClient(gateway, operation, metricRegistry);
        return new GatewayClient(client, gatewayUrlMap, sessionIdentifier, metricRegistry);
    }

    public GatewayClient createGatewayClient(PaymentGatewayName gateway,
                                             Map<String, String> gatewayUrlMap, 
                                             BiFunction<GatewayOrder, Builder, Builder> sessionIdentifier,
                                             MetricRegistry metricRegistry)
    {
        Client client = clientFactory.createWithDropwizardClient(gateway, metricRegistry);
        return new GatewayClient(client, gatewayUrlMap, sessionIdentifier, metricRegistry);
    }
}
