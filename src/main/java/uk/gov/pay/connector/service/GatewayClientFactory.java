package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.function.BiFunction;

public class GatewayClientFactory {

    final ClientFactory clientFactory;

    @Inject
    public GatewayClientFactory(ClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public GatewayClient createGatewayClient(PaymentGatewayName gateway, GatewayOperation operation, Map<String, String> gatewayUrlMap,
                                             MediaType mediaType,
                                             BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> sessionIdentier, MetricRegistry metricRegistry) {

        Client client = clientFactory.createWithDropwizardClient(gateway, operation);
        return new GatewayClient(client, gatewayUrlMap, mediaType, sessionIdentier, metricRegistry);
    }

}
