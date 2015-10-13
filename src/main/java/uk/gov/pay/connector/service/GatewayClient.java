package uk.gov.pay.connector.service;

import fj.data.Either;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.glassfish.jersey.internal.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.util.XMLUnmarshaller;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;

import java.net.UnknownHostException;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static uk.gov.pay.connector.model.GatewayError.baseGatewayError;
import static uk.gov.pay.connector.model.GatewayError.malformedResponseReceivedFromGateway;
import static uk.gov.pay.connector.model.GatewayError.unknownHostException;

public class GatewayClient {
    private final Logger logger = LoggerFactory.getLogger(GatewayClient.class);

    private final Client client;
    private final String gatewayUrl;

    private GatewayClient(Client client, String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
        this.client = client;
    }

    public static GatewayClient createGatewayClient(String gatewayUrl) {
        ClientConfig clientConfig = new ClientConfig();
        ConnectorProvider provider = new ApacheConnectorProvider();
        clientConfig.connectorProvider(provider);
        Client client = ClientBuilder
                .newBuilder()
                .withConfig(clientConfig)
                .build();
        return createGatewayClient(client, gatewayUrl);
    }

    public static GatewayClient createGatewayClient(Client client, String gatewayUrl) {
        return new GatewayClient(client, gatewayUrl);
    }

    public Either<GatewayError, Response> postXMLRequestFor(GatewayAccount account, String request) {
        try {
            return right(
                    client.target(gatewayUrl)
                            .request(APPLICATION_XML)
                            .header(AUTHORIZATION, encode(account.getUsername(), account.getPassword()))
                            .post(Entity.xml(request))
            );
        } catch (ProcessingException pe) {
            if (pe.getCause() != null && pe.getCause() instanceof UnknownHostException) {
                logger.error(format("DNS resolution error for gateway url=%s", gatewayUrl), pe);
                return left(unknownHostException("Gateway Url DNS resolution error"));
            }
            return left(baseGatewayError(pe.getMessage()));
        }
    }

    public <T> Either<GatewayError, T> unmarshallResponse(Response response, Class<T> clazz) {
        String payload = response.readEntity(String.class);
        logger.debug("response payload=" + payload);
        try {
            return right(XMLUnmarshaller.unmarshall(payload, clazz));
        } catch (JAXBException e) {
            String error = format("Could not unmarshall response %s.", payload);
            logger.error(error, e);
            return left(malformedResponseReceivedFromGateway("Invalid Response Received From Gateway"));
        }
    }

    private static String encode(String username, String password) {
        return "Basic " + Base64.encodeAsString(username + ":" + password);
    }
}
