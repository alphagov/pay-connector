package uk.gov.pay.connector.unit.service;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.GatewayClient;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.net.UnknownHostException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;

public class GatewayClientTest {
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
}
