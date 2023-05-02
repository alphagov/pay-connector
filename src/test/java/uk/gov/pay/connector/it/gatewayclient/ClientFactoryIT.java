package uk.gov.pay.connector.it.gatewayclient;

import com.codahale.metrics.MetricRegistry;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.service.payments.commons.testing.port.PortFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import java.net.SocketTimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.lang.String.format;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;

@RunWith(MockitoJUnitRunner.class)
public class ClientFactoryIT {

    private DropwizardTestSupport<ConnectorConfiguration> app;
    
    private static String DEFAULT_DROPWIZARD_CONFIG = "config/test-it-config.yaml";

    @Mock
    MetricRegistry mockMetricRegistry;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(PortFactory.findFreePort());

    @Before
    public void setup() {
        wireMockRule.resetAll();
    }
    
    @After
    public void after() {
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        app.after();
    }

    @Test
    public void shouldProxyRequestToTargetServer_whenProxyEnabled() {
        app = startApp(DEFAULT_DROPWIZARD_CONFIG, true);

        wireMockRule.stubFor(get(urlPathEqualTo("/hello"))
                .willReturn(aResponse().withBody("world").withStatus(200)));

        Client client = new ClientFactory(app.getEnvironment(), app.getConfiguration())
                .createWithDropwizardClient(WORLDPAY, AUTHORISE, mockMetricRegistry);

        client.target(getServerUrl()).path("hello").request().get();

        wireMockRule.verify(1, getRequestedFor(urlEqualTo("/hello")));
        assertEquals(90000, client.getConfiguration().getProperty(ClientProperties.READ_TIMEOUT));
    }

    @Test
    public void shouldNotProxyRequestToTargetServer_whenProxyDisabled() {
        app = startApp(DEFAULT_DROPWIZARD_CONFIG, false);

        wireMockRule.stubFor(get(urlPathEqualTo("/hello"))
                .willReturn(aResponse().withBody("world").withStatus(200)));
        Client client = new ClientFactory(app.getEnvironment(), app.getConfiguration())
                .createWithDropwizardClient(WORLDPAY, AUTHORISE, mockMetricRegistry);

        client.target(getServerUrl()).path("hello").request().get();

        wireMockRule.verify(1, getRequestedFor(urlEqualTo("/hello")));
        assertEquals(90000, client.getConfiguration().getProperty(ClientProperties.READ_TIMEOUT));
    }

    @Test
    public void anHttpRequestShouldTimeOut_whenCustomJerseyReadTimeoutIsConfigured() {
        app = startApp("config/client-factory-test-config.yaml", false);

        String path = "/hello";

        wireMockRule.stubFor(get(urlPathEqualTo("/hello"))
                .willReturn(aResponse().withBody("world").withStatus(200).withFixedDelay(2000)));
        
        Client client = new ClientFactory(app.getEnvironment(), app.getConfiguration())
                .createWithDropwizardClient(WORLDPAY, AUTHORISE, mockMetricRegistry);

        Invocation.Builder request = client.target(getServerUrl()).path(path).request();
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

    @Test
    public void shouldUseGatewaySpecificReadTimeoutOverride_whenSpecified() {
        app = startApp("config/client-factory-test-config-with-worldpay-timeout-override.yaml", false);

        String path = "/hello";
        
        wireMockRule.stubFor(get(urlPathEqualTo("/hello"))
                .willReturn(aResponse().withBody("world").withStatus(200).withFixedDelay(2000)));

        Client client = new ClientFactory(app.getEnvironment(), app.getConfiguration())
                .createWithDropwizardClient(WORLDPAY, AUTHORISE, mockMetricRegistry);

        Invocation.Builder request = client.target(getServerUrl()).path(path).request();

        Long authOverriddenTimeout = app.getConfiguration().getWorldpayConfig().getJerseyClientOverrides()
                .map(override -> override.getAuth().getReadTimeout().toMicroseconds())
                .orElse(0L);
        assertGatewayFailure(request, authOverriddenTimeout);
    }

    private void assertGatewayFailure(Invocation.Builder request, Long authOverriddenTimeout) {
        long startTime = System.currentTimeMillis();

        try {
            request.get();
            fail();
        } catch (javax.ws.rs.ProcessingException e) {
            long endTime = System.currentTimeMillis();

            Throwable timeoutException = e.getCause();
            assertThat(timeoutException, instanceOf(SocketTimeoutException.class));

            final long connectionOverheadInMillis = 1000;

            long expectedTimeout = authOverriddenTimeout + connectionOverheadInMillis;

            long actualDuration = endTime - startTime;

            assertThat(actualDuration, lessThan(expectedTimeout));
        }

    }

    private DropwizardTestSupport<ConnectorConfiguration> startApp(String configuration, boolean proxyEnabled) {
        final String PROXY_HOST_PROPERTY = "https.proxyHost";
        final String PROXY_PORT_PROPERTY = "https.proxyPort";

        app = new DropwizardTestSupport<>(ConnectorApp.class,
                ResourceHelpers.resourceFilePath(configuration));
        if (proxyEnabled) {
            System.setProperty(PROXY_HOST_PROPERTY, "localhost");
            System.setProperty(PROXY_PORT_PROPERTY, String.valueOf(wireMockRule.port()));
        } else {
            System.clearProperty(PROXY_HOST_PROPERTY);
            System.clearProperty(PROXY_PORT_PROPERTY);
        }
        try {
            app.before();
            return app;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String getServerUrl() {
        return format("http://localhost:%s", wireMockRule.port());
    }
}
