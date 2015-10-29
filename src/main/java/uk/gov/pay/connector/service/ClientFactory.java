package uk.gov.pay.connector.service;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientProperties;
import uk.gov.pay.connector.app.ConnectorConfiguration;

import javax.ws.rs.client.Client;

public class ClientFactory {
    private final Environment environment;
    private final ConnectorConfiguration conf;

    public ClientFactory(Environment environment, ConnectorConfiguration conf) {
        this.environment = environment;
        this.conf = conf;
    }

    public Client createWithDropwizardClient(String name) {
        JerseyClientConfiguration clientConfiguration = conf.getClientConfiguration();
        ApacheConnectorProvider connectorProvider = new ApacheConnectorProvider();

        Duration readTimeout = conf.getCustomJerseyClient().getReadTimeout();
        int readTimeoutInMillis = (int) (readTimeout.toMilliseconds());

        return new JerseyClientBuilder(environment)
                .using(connectorProvider)
                .using(clientConfiguration)
                .withProperty(ClientProperties.READ_TIMEOUT, readTimeoutInMillis)
                .build(name);
    }
}
