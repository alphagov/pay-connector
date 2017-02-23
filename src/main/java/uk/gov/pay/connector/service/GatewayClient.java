package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
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
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.MediaType;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.model.GatewayError.*;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.util.AuthUtil.encode;

public class GatewayClient {
    private final Logger logger = LoggerFactory.getLogger(GatewayClient.class);

    private final Client client;
    private final Map<String, String> gatewayUrlMap;
    private final MetricRegistry metricRegistry;
    private final MediaType mediaType;
    private final String sessionIdentifierName;

    private GatewayClient(Client client, Map<String, String> gatewayUrlMap, MediaType mediaType,
                          String sessionIdentifierName, MetricRegistry metricRegistry) {
        this.gatewayUrlMap = gatewayUrlMap;
        this.client = client;
        this.metricRegistry = metricRegistry;
        this.mediaType = mediaType;
        this.sessionIdentifierName = sessionIdentifierName;
    }

    public static GatewayClient createGatewayClient(Client client, Map<String, String> gatewayUrlMap,
                                                    MediaType mediaType, String sessionIdentifierName, MetricRegistry metricRegistry) {
        return new GatewayClient(client, gatewayUrlMap, mediaType, sessionIdentifierName, metricRegistry);
    }

    public Either<GatewayError, GatewayClient.Response> postRequestFor(GatewayAccountEntity account, GatewayOrder request) {
        return postRequestFor(null, account, request);
    }

    public Either<GatewayError, GatewayClient.Response> postRequestFor(String route, GatewayAccountEntity account, GatewayOrder request) {
        String metricsPrefix = String.format("gateway-operations.%s.%s.%s", account.getGatewayName(), account.getType(), request.getOrderRequestType());
        javax.ws.rs.core.Response response = null;

        String gatewayUrl = gatewayUrlMap.get(account.getType());
        if (route != null) {
            gatewayUrl = String.format("%s/%s", gatewayUrl, route);
        }

        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
        try {
            logger.info("POSTing request for account '{}' with type '{}'", account.getGatewayName(), account.getType());
            Builder requestBuilder = client.target(gatewayUrl)
                    .request()
                    .header(AUTHORIZATION, encode(
                            account.getCredentials().get(CREDENTIALS_USERNAME),
                            account.getCredentials().get(CREDENTIALS_PASSWORD)));

                    response = request.getProviderSessionId()
                            .map(sessionId -> requestBuilder.cookie(sessionIdentifierName, sessionId))
                            .orElseGet(() -> requestBuilder)
                            .post(Entity.entity(request.getPayload(), mediaType));

            int statusCode = response.getStatus();
            if (statusCode == OK.getStatusCode()) {
                return right(new GatewayClient.Response(response));
            } else {
                logger.error("Gateway returned unexpected status code: {}, for gateway url={} with type {}", statusCode, gatewayUrl, account.getType());
                incrementFailureCounter(metricRegistry, metricsPrefix);
                return left(unexpectedStatusCodeFromGateway("Unexpected Response Code From Gateway"));
            }
        } catch (ProcessingException pe) {
            incrementFailureCounter(metricRegistry, metricsPrefix);
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
            incrementFailureCounter(metricRegistry, metricsPrefix);
            logger.error(format("Exception for gateway url=%s", gatewayUrl), e);
            return left(baseError(e.getMessage()));
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram(metricsPrefix + ".response_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
            if (response != null) {
                response.close();
            }
        }
    }

    public <T> Either<GatewayError, T> unmarshallResponse(GatewayClient.Response response, Class<T> clazz) {
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

    private void incrementFailureCounter(MetricRegistry metricRegistry, String metricsPrefix) {
        metricRegistry.counter(metricsPrefix + ".failures").inc();
    }
    static public class Response {
        private final int status;
        private final String entity;
        private final Map<String, String> responseCookies = new HashMap<>();

        protected Response(final javax.ws.rs.core.Response delegate) {
            this.status = delegate.getStatus();
            this.entity = delegate.readEntity(String.class);
            delegate.getCookies().forEach((name,cookie) -> {
                responseCookies.put(name, cookie.getValue());
            });
        }

        public int getStatus() {
            return status;
        }

        public String getEntity() {
            return entity;
        }

        public Map<String, String> getResponseCookies() {
            return responseCookies;
        }

    }
}
