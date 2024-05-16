package uk.gov.pay.connector.util;

import org.glassfish.jersey.apache5.connector.Apache5ConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.spi.ConnectorProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import static org.glassfish.jersey.client.RequestEntityProcessing.BUFFERED;

public class TestClientFactory {
    public static Client createJerseyClient() {
        return createClientWithApacheConnectorAndTimeout(-1);
    }

    public static Client createClientWithApacheConnectorAndTimeout(int readTimeout) {
        ClientConfig clientConfig = new ClientConfig();
        ConnectorProvider provider = new Apache5ConnectorProvider();
        clientConfig.connectorProvider(provider);
        if (readTimeout > 0) {
            clientConfig.property(ClientProperties.READ_TIMEOUT, readTimeout);
        }
        clientConfig.property(ClientProperties.REQUEST_ENTITY_PROCESSING, BUFFERED);

        Client client = ClientBuilder
                .newBuilder()
                .withConfig(clientConfig)
                .build();

        return client;
    }
}
