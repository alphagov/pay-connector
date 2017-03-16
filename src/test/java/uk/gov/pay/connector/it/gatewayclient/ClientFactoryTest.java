package uk.gov.pay.connector.it.gatewayclient;

import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import jdk.nashorn.internal.runtime.regexp.joni.Config;
import org.glassfish.jersey.client.ClientProperties;
import org.hamcrest.core.IsInstanceOf;
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
import javax.ws.rs.client.Invocation;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.dropwizard.testing.ConfigOverride.config;
import static java.lang.String.format;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
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

    @Test
    public void anHttpRequestShouldTimeOut_whenCustomJerseyReadTimeoutIsConfigured() throws Exception {
        app = new DropwizardTestSupport<>(ConnectorApp.class,
                ResourceHelpers.resourceFilePath("config/client-factory-test-config.yaml"));
        app.before();

        String path = "/hello";
        mockServer
                .when(request().withMethod("GET").withPath(path))
                .respond(response("world").withStatusCode(200).withDelay(TimeUnit.MILLISECONDS, 2000));

        Client client = new ClientFactory(app.getEnvironment(), app.getConfiguration())
                .createWithDropwizardClient("WORLDPAY", GatewayOperation.AUTHORISE);

        Invocation.Builder request = client.target(serverUrl).path(path).request();
        long startTime = System.currentTimeMillis();
        try {
            request.get();
            fail();
        } catch (javax.ws.rs.ProcessingException e) {
            long endTime = System.currentTimeMillis();

            Throwable timeoutException = e.getCause();
            assertThat(timeoutException, instanceOf(SocketTimeoutException.class));

            /*

            There are three settings which control timeouts in the Jersey client:

            1. timeout:

            Defines the socket timeout (SO_TIMEOUT), which is the
            timeout for waiting for data or, put differently, a maximum period inactivity
            between two consecutive data packets).

            2. connectionTimeout:

            Determines the timeout until a connection is established

            3. readTimeout:

            Sets the read timeout to a specified timeout, in
            milliseconds. A non-zero value specifies the timeout when
            reading from Input stream when a connection is established to a
            resource. If the timeout expires before there is data available
            for read, a java.net.SocketTimeoutException is raised. A
            timeout of zero is interpreted as an infinite timeout.

            Within the execution of this test, the value of `timeout` does not have any effect,
            because we're using a mock server, so we don't expect inter-packet delays.

            We are also unlikely to suffer from delays establishing the connection.

            However there is some overhead in the overall request process which is hard to quantify (on a
            dev laptop the overhead was about 140ms).

            In order to avoid random test failures, we've chosen a generous overhead value.

            We assert that the request timed out, and that it was less than the configure readTimeout value, plus
            the overhead.
            */
            final long connectionOverheadInMillis = 1000;
            long expectedTimeout = app.getConfiguration().getCustomJerseyClient().getReadTimeout().toMilliseconds()
                    + connectionOverheadInMillis;

            long actualDuration = endTime - startTime;

            assertThat(actualDuration, lessThan(expectedTimeout));
        }
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
