package uk.gov.pay.connector.it.gatewayclient;

import org.glassfish.jersey.client.ClientProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockserver.integration.ClientAndProxy;
import org.mockserver.integration.ClientAndServer;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.service.ClientFactory;

import javax.ws.rs.client.Client;
import java.util.Arrays;
import java.util.Collection;

import static io.dropwizard.testing.ConfigOverride.config;
import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.socket.PortFactory.findFreePort;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.mockserver.verify.VerificationTimes.once;

@RunWith(Parameterized.class)
public class ClientFactoryTest {

    @Rule
    public DropwizardAppWithPostgresRule app;

    private ClientAndProxy proxy;
    private ClientAndServer mockServer;
    private int serverPort = findFreePort();
    private int proxyPort = findFreePort();
    private String serverUrl = format("http://localhost:%s", serverPort);
    private Boolean proxyEnabled;

    @Parameterized.Parameters
    public static Collection<Boolean> data() {
        return Arrays.asList(Boolean.TRUE, Boolean.FALSE);
    }

    public ClientFactoryTest(Boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
        startAppWithProxy(proxyEnabled);
    }

    @Before
    public void before() throws Exception {
        mockServer = startClientAndServer(serverPort);
        proxy = new ClientAndProxy(proxyPort, "localhost", serverPort);
    }

    @After
    public void after() throws Exception {
        proxy.stop();
        mockServer.stop();
    }

    /**
     * DropwizardAppWithPostgresRule must be constructed during Test construction,
     * hence this Parameterised Test and the conditional assertion below.
     *
     */
    @Test
    public void shouldCreateAJerseyClientWithProxyEnabled() throws Exception {
        mockServer
                .when(request().withMethod("GET").withPath("/hello"))
                .respond(response("world").withStatusCode(200));


        Client client = new ClientFactory(app.getEnvironment(), app.getConf())
                .createWithDropwizardClient("SANDBOX");

        client.target(serverUrl).path("hello").request().get();

        mockServer.verify(request().withPath("/hello"), once());
        assertEquals(90000, client.getConfiguration().getProperty(ClientProperties.READ_TIMEOUT));

        if (proxyEnabled) {
            proxy.verify(request().withPath("/hello"), once());
        } else {
            proxy.verify(request().withPath("/hello"), exactly(0));
        }

    }

    private void startAppWithProxy(boolean proxyEnabled) {
        app = new DropwizardAppWithPostgresRule(
                config("customJerseyClient.enableProxy", String.valueOf(proxyEnabled)),
                config("jerseyClient.proxy.port", String.valueOf(proxyPort)));
    }

}
