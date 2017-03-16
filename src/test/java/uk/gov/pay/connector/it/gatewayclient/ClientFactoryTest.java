package uk.gov.pay.connector.it.gatewayclient;

import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import jdk.nashorn.internal.runtime.regexp.joni.Config;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.verification.VerificationMode;
import org.mockserver.integration.ClientAndProxy;
import org.mockserver.integration.ClientAndServer;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.service.ClientFactory;
import uk.gov.pay.connector.service.GatewayOperation;

import javax.ws.rs.client.Client;
import java.util.Arrays;
import java.util.Collection;

import static io.dropwizard.testing.ConfigOverride.config;
import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.socket.PortFactory.findFreePort;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.mockserver.verify.VerificationTimes.once;

public class ClientFactoryTest {

    DropwizardTestSupport<ConnectorConfiguration> app;
    private ClientAndProxy proxy;
    private ClientAndServer mockServer;
    private int serverPort = findFreePort();
    private int proxyPort = findFreePort();
    private String serverUrl = format("http://localhost:%s", serverPort);

    @Before
    public void setup() {
        proxy = new ClientAndProxy(proxyPort, "localhost", serverPort);
        mockServer = startClientAndServer(serverPort);
    }

    @After
    public void after() throws Exception {
        proxy.stop();
        mockServer.stop();
        app.after();
    }

    @Test
    public void shouldProxyRequestToTargetServer_whenProxyEnabled() throws Exception {
        app = startAppWithProxy(true);

        mockServer
                .when(request().withMethod("GET").withPath("/hello"))
                .respond(response("world").withStatusCode(200));


        Client client = new ClientFactory(app.getEnvironment(), app.getConfiguration())
                .createWithDropwizardClient("SANDBOX", GatewayOperation.AUTHORISE);

        client.target(serverUrl).path("hello").request().get();

        mockServer.verify(request().withPath("/hello"), once());
        assertEquals(90000, client.getConfiguration().getProperty(ClientProperties.READ_TIMEOUT));

        proxy.verify(request().withPath("/hello"), once());
    }

    @Test
    public void shouldNotProxyRequestToTargetServer_whenProxyDisabled() throws Exception {
        app = startAppWithProxy(false);

        mockServer
                .when(request().withMethod("GET").withPath("/hello"))
                .respond(response("world").withStatusCode(200));

        Client client = new ClientFactory(app.getEnvironment(), app.getConfiguration())
                .createWithDropwizardClient("SANDBOX", GatewayOperation.AUTHORISE);

        client.target(serverUrl).path("hello").request().get();

        mockServer.verify(request().withPath("/hello"), once());
        assertEquals(90000, client.getConfiguration().getProperty(ClientProperties.READ_TIMEOUT));

        proxy.verify(request().withPath("/hello"), exactly(0));
    }

    private DropwizardTestSupport<ConnectorConfiguration> startAppWithProxy(boolean proxyEnabled) {
        DropwizardTestSupport<ConnectorConfiguration> app = new DropwizardTestSupport<>(ConnectorApp.class,
                ResourceHelpers.resourceFilePath("config/test-it-config.yaml"),
                ConfigOverride.config("customJerseyClient.enableProxy", String.valueOf(proxyEnabled)),
                ConfigOverride.config("jerseyClient.proxy.port", String.valueOf(proxyPort)));
        app.before();
        return app;
    }
}
