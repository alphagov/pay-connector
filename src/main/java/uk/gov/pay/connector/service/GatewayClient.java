package uk.gov.pay.connector.service;

import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.util.XMLUnmarshaller;
import uk.gov.pay.connector.util.XMLUnmarshallerException;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.model.GatewayError.*;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.util.AuthUtil.encode;

public class GatewayClient {
    private final Logger logger = LoggerFactory.getLogger(GatewayClient.class);

    private final Client client;
    private final String gatewayUrl;

    private GatewayClient(Client client, String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
        this.client = client;
    }

    public static GatewayClient createGatewayClient(Client client, String gatewayUrl) {
        return new GatewayClient(client, gatewayUrl);
    }

    public Either<GatewayError, Response> postXMLRequestFor(GatewayAccountEntity account, String request) {
        try {
            Response response = client.target(gatewayUrl)
                    .request(APPLICATION_XML)
                    .header(AUTHORIZATION, encode(
                            account.getCredentials().get(CREDENTIALS_USERNAME),
                            account.getCredentials().get(CREDENTIALS_PASSWORD)))
                    .post(Entity.xml(request));
            int statusCode = response.getStatus();
            if (statusCode == OK.getStatusCode()) {
                return right(response);
            } else {
                logger.error(format("Gateway returned unexpected status code: %d, for gateway url=%s", statusCode, gatewayUrl));
                return left(unexpectedStatusCodeFromGateway("Unexpected Response Code From Gateway"));
            }
        } catch (ProcessingException pe) {
            if (pe.getCause() != null) {
                if (pe.getCause() instanceof UnknownHostException) {
                    logger.error(format("DNS resolution error for gateway url=%s", gatewayUrl), pe);
                    return left(unknownHostException("Gateway Url DNS resolution error"));
                }
                if (pe.getCause() instanceof SocketTimeoutException) {
                    logger.error(format("Connection timed out error for gateway url=%s", gatewayUrl), pe);
                    return left(gatewayConnectionTimeoutException("Gateway connection timeout error"));
                }
                if (pe.getCause() instanceof SocketException) {
                    logger.error(format("Socket Exception for gateway url=%s", gatewayUrl), pe);
                    return left(gatewayConnectionSocketException("Gateway connection socket error"));
                }
            }
            logger.error(format("Exception for gateway url=%s", gatewayUrl), pe);
            return left(baseGatewayError(pe.getMessage()));
        } catch (Exception e) {
            logger.error(format("Exception for gateway url=%s", gatewayUrl), e);
            return left(baseGatewayError(e.getMessage()));
        }
    }

    public <T> Either<GatewayError, T> unmarshallResponse(Response response, Class<T> clazz) {
        String payload = response.readEntity(String.class);
        logger.debug("response payload=" + payload);
        try {
            return right(XMLUnmarshaller.unmarshall(payload, clazz));
        } catch (XMLUnmarshallerException e) {
            String error = format("Could not unmarshall response %s.", payload);
            logger.error(error, e);
            return left(malformedResponseReceivedFromGateway("Invalid Response Received From Gateway"));
        }
    }
}
