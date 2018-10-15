package uk.gov.pay.connector.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.Test;
import uk.gov.pay.connector.util.PortFactory;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static uk.gov.pay.connector.gateway.util.AuthUtil.encode;
import static uk.gov.pay.connector.util.TestClientFactory.createClientWithApacheConnectorAndTimeout;
import static uk.gov.pay.connector.util.TestClientFactory.createJerseyClient;

public class GatewayClientExploratoryTest {

    @Test
    public void connectionToInvalidUrlUsingDefaultJerseyConnectorProvider() {
        Client client = ClientBuilder.newClient();
        String gatewayUrl = "http://invalidone.invalid";
        try {
            postXMLRequestFor(client, gatewayUrl, "<request/>");
            fail("Exception not thrown!");
        } catch (Exception e) {
            assertTrue(e instanceof ProcessingException);
            assertTrue(e.getCause() instanceof UnknownHostException);
        }
    }

    @Test
    public void connectionToInvalidUrlUsingApacheConnectorProvider() {
        Client client = createJerseyClient();
        String gatewayUrl = "http://invalidone.invalid";
        try {
            postXMLRequestFor(client, gatewayUrl, "<request/>");
            fail("Exception not thrown!");
        } catch (Exception e) {
            assertTrue(e instanceof ProcessingException);
            assertTrue(e.getCause() instanceof UnknownHostException);
        }
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

        try {
            postXMLRequestFor(client, gatewayUrl, "<request/>");
            fail("Exception not thrown!");
        } catch (Exception e) {
            assertTrue(e.getMessage(), e instanceof ProcessingException);
            assertTrue(e.getMessage(), e.getCause() instanceof SocketTimeoutException);
        } finally {
            wireMockServer.stop();
        }
    }

    public Response postXMLRequestFor(Client client, String gatewayUrl, String requestBody) {
        return client.target(gatewayUrl)
                .request(APPLICATION_XML)
                .header(AUTHORIZATION, encode("user", "pass"))
                .post(Entity.xml(requestBody));
    }
}
