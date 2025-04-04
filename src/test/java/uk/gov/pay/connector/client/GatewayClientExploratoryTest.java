package uk.gov.pay.connector.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import uk.gov.service.payments.commons.testing.port.PortFactory;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.TestClientFactory.createClientWithApacheConnectorAndTimeout;
import static uk.gov.pay.connector.util.TestClientFactory.createJerseyClient;

@Disabled //this should be run manually
public class GatewayClientExploratoryTest {

    @Test
    public void connectionToInvalidUrlUsingDefaultJerseyConnectorProvider() {
        Client client = ClientBuilder.newClient();
        String gatewayUrl = "http://invalidone.invalid";
        var ex = assertThrows(ProcessingException.class, () -> postXMLRequestFor(client, gatewayUrl, "<request/>"));
        assertThat(UnknownHostException.class, is(ex.getCause().getClass()));
    }

    @Test
    public void connectionToInvalidUrlUsingApacheConnectorProvider() {
        Client client = createJerseyClient();
        String gatewayUrl = "http://invalidone.invalid";
        var ex = assertThrows(ProcessingException.class, () -> postXMLRequestFor(client, gatewayUrl, "<request/>"));
        assertThat(UnknownHostException.class, is(ex.getCause().getClass()));
    }

    @Test
    public void connectionReadTimeoutTest() {
        int port = PortFactory.findFreePort();
        WireMockConfiguration wireMockConfig = wireMockConfig().port(port);
        WireMockServer wireMockServer = new WireMockServer(wireMockConfig);

        wireMockServer.start();

        WireMock.configureFor("localhost", port);
        int delay = 10000;
        stubFor(
                post(urlPathEqualTo("/pal/servlet/soap/Payment"))
                        .willReturn(
                                aResponse()
                                        .withFixedDelay(delay)
                        )
        );

        String gatewayUrl = "http://localhost:" + port + "/pal/servlet/soap/Payment";

        Client client = createClientWithApacheConnectorAndTimeout(500);

        var ex = assertThrows(ProcessingException.class, () -> postXMLRequestFor(client, gatewayUrl, "<request/>"));
        assertThat(SocketTimeoutException.class, is(ex.getCause().getClass()));
        
        wireMockServer.stop();
    }

    public Response postXMLRequestFor(Client client, String gatewayUrl, String requestBody) {
        return client.target(gatewayUrl)
                .request(APPLICATION_XML)
                .post(Entity.xml(requestBody));
    }
}
