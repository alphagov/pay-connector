package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayErrorException.GatewayConnectionErrorException;
import uk.gov.pay.connector.gateway.GatewayErrorException.GatewayConnectionTimeoutErrorException;
import uk.gov.pay.connector.gateway.GatewayErrorException.GenericGatewayErrorException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import java.net.HttpCookie;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.pay.connector.gateway.util.AuthUtil.encode;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

public class GatewayClient {
    private static final Logger logger = LoggerFactory.getLogger(GatewayClient.class);

    private final Client client;
    private final Map<String, String> gatewayUrlMap;
    private final MetricRegistry metricRegistry;

    public GatewayClient(Client client, Map<String, String> gatewayUrlMap, MetricRegistry metricRegistry) {
        this.gatewayUrlMap = gatewayUrlMap;
        this.client = client;
        this.metricRegistry = metricRegistry;
    }

    public GatewayClient.Response postRequestFor(String route, GatewayAccountEntity account, GatewayOrder request) 
            throws GenericGatewayErrorException, GatewayConnectionErrorException, GatewayConnectionTimeoutErrorException {
        return postRequestFor(route, account, request, emptyList());
    }
    
    public GatewayClient.Response postRequestFor(String route,
                                                 GatewayAccountEntity account,
                                                 GatewayOrder request,
                                                 List<HttpCookie> cookies) 
            throws GenericGatewayErrorException, GatewayConnectionTimeoutErrorException, GatewayConnectionErrorException {
        String metricsPrefix = format("gateway-operations.%s.%s.%s", account.getGatewayName(), account.getType(), request.getOrderRequestType());
        javax.ws.rs.core.Response response = null;

        String gatewayUrl = gatewayUrlMap.get(account.getType());
        if (route != null) {
            gatewayUrl = String.format("%s/%s", gatewayUrl, route);
        }

        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
        try {
            logger.info("POSTing request for account '{}' with type '{}'", account.getGatewayName(), account.getType());
            Builder requestBuilder = client.target(gatewayUrl).request().header(AUTHORIZATION, encode(
                            account.getCredentials().get(CREDENTIALS_USERNAME),
                            account.getCredentials().get(CREDENTIALS_PASSWORD)));
            
            cookies.forEach(cookie -> requestBuilder.cookie(cookie.getName(), cookie.getValue()));
            response = requestBuilder.post(Entity.entity(request.getPayload(), request.getMediaType()));
            int statusCode = response.getStatus();
            Response gatewayResponse = new Response(response);
            if (statusCode == OK.getStatusCode()) {
                return gatewayResponse;
            } else {
                logger.error("Gateway returned unexpected status code: {}, for gateway url={} with type {}", statusCode, gatewayUrl, account.getType());
                incrementFailureCounter(metricRegistry, metricsPrefix);
                throw new GatewayConnectionErrorException("Unexpected HTTP status code " + statusCode + " from gateway");
            }
        } catch (ProcessingException pe) {
            incrementFailureCounter(metricRegistry, metricsPrefix);
            if (pe.getCause() != null) {
                if (pe.getCause() instanceof SocketTimeoutException) {
                    logger.error(format("Connection timed out error for gateway url=%s", gatewayUrl), pe);
                    throw new GatewayConnectionTimeoutErrorException("Gateway connection timeout error");
                }
            }
            logger.error(format("Exception for gateway url=%s, error message: %s", gatewayUrl, pe.getMessage()), pe);
            throw new GenericGatewayErrorException(pe.getMessage());
        } catch(GatewayConnectionErrorException e) {
            throw e;
        } catch (Exception e) {
            incrementFailureCounter(metricRegistry, metricsPrefix);
            logger.error(format("Exception for gateway url=%s", gatewayUrl), e);
            throw new GenericGatewayErrorException(e.getMessage());
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram(metricsPrefix + ".response_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
            if (response != null) {
                response.close();
            }
        }
    }
    
    private void incrementFailureCounter(MetricRegistry metricRegistry, String metricsPrefix) {
        metricRegistry.counter(metricsPrefix + ".failures").inc();
    }
    
    public static class Response {
        private final int status;
        private final String entity;
        private final Map<String, String> responseCookies = new HashMap<>();

        protected Response(final javax.ws.rs.core.Response delegate) {
            this.status = delegate.getStatus();
            this.entity = delegate.readEntity(String.class);
            delegate.getCookies().forEach((name, cookie) -> {
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
