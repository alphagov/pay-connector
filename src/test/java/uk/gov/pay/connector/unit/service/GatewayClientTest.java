package uk.gov.pay.connector.unit.service;

import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.it.contract.SmartpayMockClient;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.util.PortFactory;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;
import static uk.gov.pay.connector.service.OrderCaptureRequestBuilder.aSmartpayOrderCaptureRequest;

public class GatewayClientTest {
    private static final String TRANSACTION_ID = "7914440428682669";
    private static final String MERCHANT_CODE = "MerchantAccount";
    public static final int JERSEY_READ_TIMEOUT = 600;

    private int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    @Test
    public void connectionToInvalidUrlThrowsException(){
        Client client = ClientBuilder.newClient();
        String gatewayUrl = "http://invalidone.invalid";
        GatewayClient gatewayClient = createGatewayClient(client, gatewayUrl);
        GatewayAccount account = gatewayAccountFor("user", "pass");
        try {
            gatewayClient.postXMLRequestFor(account, "<request/>");
        } catch (Exception e) {
            assertTrue(e instanceof ProcessingException);
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertThat(e.getMessage(), is("Already connected"));
        }
    }

    @Test
    public void connectionToInvalidUrlUsingApacheConnectorProvider(){
        String gatewayUrl = "http://invalidone.invalid";
        GatewayClient gatewayClient = createGatewayClient(gatewayUrl);
        GatewayAccount account = gatewayAccountFor("user", "pass");
        try {
            gatewayClient.postXMLRequestFor(account, "<request/>");
        } catch (Exception e) {
            assertTrue(e instanceof ProcessingException);
            assertTrue(e.getCause() instanceof UnknownHostException);
        }
    }

    @Test
    public void captureWithTimeoutAgainstWireMockStub() throws Exception {
        //prepare expectation
        SmartpayMockClient smartpayMock = new SmartpayMockClient(TRANSACTION_ID);

        smartpayMock.respondWithTimeoutWhenCapture();

        //debug wiremock
        List<Request> requests = new ArrayList<>();
        wireMockRule.addMockServiceRequestListener(
                (request, response) ->
                        requests.add(LoggedRequest.createFrom(request))
        );

        //prepare invocation
        String gatewayUrl = "http://localhost:" + port + "/pal/servlet/soap/Payment";

        Client client = createClient();

        GatewayClient gatewayClient = createGatewayClient(client, gatewayUrl);

        String captureRequest = aSmartpayOrderCaptureRequest()
                .withMerchantCode(MERCHANT_CODE)
                .withTransactionId(TRANSACTION_ID)
                .withAmount("1223")
                .build();

        GatewayAccount account = gatewayAccountFor("user", "pass");
        try {
            gatewayClient.postXMLRequestFor(account, captureRequest);
        } catch (Exception e) {
            assertTrue(e instanceof ProcessingException);
            assertTrue(e.getCause() instanceof SocketTimeoutException);
        }
    }

    private Client createClient() {
        ClientConfig clientConfig = new ClientConfig();

        clientConfig.getProperties();

        ConnectorProvider provider = new ApacheConnectorProvider();

        clientConfig.connectorProvider(provider);

        clientConfig.property(ClientProperties.READ_TIMEOUT, JERSEY_READ_TIMEOUT);

        Client client = ClientBuilder
                .newBuilder()
                .withConfig(clientConfig)
                .build();

        return client;
    }
}
