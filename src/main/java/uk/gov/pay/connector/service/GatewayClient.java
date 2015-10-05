package uk.gov.pay.connector.service;

import org.glassfish.jersey.internal.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.util.XMLUnmarshaller;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;

import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

public class GatewayClient {
    private final Logger logger = LoggerFactory.getLogger(GatewayClient.class);

    private final Client client;
    private final String gatewayUrl;

    public GatewayClient(Client client, String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
        this.client = client;
    }

    public Response postXMLRequestFor(GatewayAccount account, String request) {
        return client.target(gatewayUrl)
                .request(APPLICATION_XML)
                .header(AUTHORIZATION, encode(account.getUsername(), account.getPassword()))
                .post(Entity.xml(request));
    }

    public <T> T unmarshallResponse(Response response, Class<T> clazz) {
        String payload = response.readEntity(String.class);
        logger.debug("response payload=" + payload);
        try {
            return XMLUnmarshaller.unmarshall(payload, clazz);
        } catch (JAXBException e) {
            throw unmarshallException(payload, e);
        }
    }

    private RuntimeException unmarshallException(String payload, JAXBException e) {
        String error = format("Could not unmarshall response %s.", payload);
        logger.error(error, e);
        return new RuntimeException(error, e);
    }

    private static String encode(String username, String password) {
        return "Basic " + Base64.encodeAsString(username + ":" + password);
    }
}
