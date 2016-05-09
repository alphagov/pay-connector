package uk.gov.pay.connector.service;

import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.util.XMLUnmarshaller;
import uk.gov.pay.connector.util.XMLUnmarshallerException;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Link.Builder;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.model.ErrorResponse.*;
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

    public Either<ErrorResponse, GatewayClient.Response> postXMLRequestFor(GatewayAccountEntity account, String request) {
        javax.ws.rs.core.Response response = null;
        try {
            response = client.target(gatewayUrl)
                    .request(APPLICATION_XML)
                    .header(AUTHORIZATION, encode(
                            account.getCredentials().get(CREDENTIALS_USERNAME),
                            account.getCredentials().get(CREDENTIALS_PASSWORD)))
                    .post(Entity.xml(request));
            int statusCode = response.getStatus();
            if (statusCode == OK.getStatusCode()) {
                return right(new GatewayClient.Response(response));
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
            return left(baseError(pe.getMessage()));
        } catch (Exception e) {
            logger.error(format("Exception for gateway url=%s", gatewayUrl), e);
            return left(baseError(e.getMessage()));
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    public <T> Either<ErrorResponse, T> unmarshallResponse(GatewayClient.Response response, Class<T> clazz) {
        String payload = response.getEntity();
        logger.debug("response payload=" + payload);
        try {
            return right(XMLUnmarshaller.unmarshall(payload, clazz));
        } catch (XMLUnmarshallerException e) {
            String error = format("Could not unmarshall response %s.", payload);
            logger.error(error, e);
            return left(malformedResponseReceivedFromGateway("Invalid Response Received From Gateway"));
        }
    }

    static public class Response {
        private final javax.ws.rs.core.Response delegate;
        private final String entity;

        protected Response(final javax.ws.rs.core.Response delegate) {
            this.delegate = delegate;
            this.entity = delegate.readEntity(String.class);
        }

        public int getStatus() {
            return delegate.getStatus();
        }

        public javax.ws.rs.core.Response.StatusType getStatusInfo() {
            return delegate.getStatusInfo();
        }

        public String getEntity() {
            return entity;
        }

        public String toString() {
            return delegate.toString();
        }

        public MediaType getMediaType() {
            return delegate.getMediaType();
        }

        public Locale getLanguage() {
            return delegate.getLanguage();
        }

        public int getLength() {
            return delegate.getLength();
        }

        public Set<String> getAllowedMethods() {
            return delegate.getAllowedMethods();
        }

        public Map<String, NewCookie> getCookies() {
            return delegate.getCookies();
        }

        public EntityTag getEntityTag() {
            return delegate.getEntityTag();
        }

        public Date getDate() {
            return delegate.getDate();
        }

        public Date getLastModified() {
            return delegate.getLastModified();
        }

        public URI getLocation() {
            return delegate.getLocation();
        }

        public Set<Link> getLinks() {
            return delegate.getLinks();
        }

        public boolean hasLink(final String relation) {
            return delegate.hasLink(relation);
        }

        public Link getLink(final String relation) {
            return delegate.getLink(relation);
        }

        public Builder getLinkBuilder(final String relation) {
            return delegate.getLinkBuilder(relation);
        }

        public MultivaluedMap<String, Object> getMetadata() {
            return delegate.getMetadata();
        }

        public MultivaluedMap<String, Object> getHeaders() {
            return delegate.getHeaders();
        }

        public MultivaluedMap<String, String> getStringHeaders() {
            return delegate.getStringHeaders();
        }

        public String getHeaderString(final String name) {
            return delegate.getHeaderString(name);
        }
    }
}
