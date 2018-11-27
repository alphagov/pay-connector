package uk.gov.pay.connector.gateway.stripe;

import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.spi.ConnectorProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

public class TestClientFactory {
    public static Client createJerseyClient() {
        return createClientWithApacheConnectorAndTimeout(-1);
    }

    public static Client createClientWithApacheConnectorAndTimeout(int readTimeout) {
        ClientConfig clientConfig = new ClientConfig();
        ConnectorProvider provider = new ApacheConnectorProvider();
        clientConfig.connectorProvider(provider);
        if (readTimeout > 0) {
            clientConfig.property(ClientProperties.READ_TIMEOUT, readTimeout);
        }
        Client client = ClientBuilder
                .newBuilder()
                .withConfig(clientConfig)
                .build();

        return client;
    }
}
