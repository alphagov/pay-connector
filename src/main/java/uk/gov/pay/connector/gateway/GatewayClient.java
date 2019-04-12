package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GenericGatewayException;
import uk.gov.pay.connector.gateway.model.request.GatewayClientRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import java.net.HttpCookie;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static javax.ws.rs.core.Response.Status.OK;

public class GatewayClient {
    private static final Logger logger = LoggerFactory.getLogger(GatewayClient.class);

    private final Client client;
    private final MetricRegistry metricRegistry;

    public GatewayClient(Client client, MetricRegistry metricRegistry) {
        this.client = client;
        this.metricRegistry = metricRegistry;
    }

    public GatewayClient.Response postRequestFor(URI url, GatewayAccountEntity account, GatewayOrder request, Map<String, String> headers) 
            throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayConnectionTimeoutException {
        return postRequestFor(url, account, request, emptyList(), headers);
    }
    
    public GatewayClient.Response postRequestFor(GatewayClientRequest request)
            throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayConnectionTimeoutException {
        return postRequestFor(request.getUrl(), request.getGatewayAccount(), request.getGatewayOrder(), request.getHeaders());
    }

    public GatewayClient.Response postRequestFor(URI url, 
                                                 GatewayAccountEntity account, 
                                                 GatewayOrder request, 
                                                 List<HttpCookie> cookies, 
                                                 Map<String, String> headers) 
            throws GatewayException.GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {
        
        String metricsPrefix = format("gateway-operations.%s.%s.%s", account.getGatewayName(), account.getType(), request.getOrderRequestType());
        javax.ws.rs.core.Response response = null;

        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
        try {
            logger.info("POSTing request for account '{}' with type '{}'", account.getGatewayName(), account.getType());
            
            Builder requestBuilder = client.target(url).request();
            headers.keySet().forEach(headerKey -> requestBuilder.header(headerKey, headers.get(headerKey)));
            cookies.forEach(cookie -> requestBuilder.cookie(cookie.getName(), cookie.getValue()));
            response = requestBuilder.post(Entity.entity(request.getPayload(), request.getMediaType()));
            int statusCode = response.getStatus();
            Response gatewayResponse = new Response(response);
            if (statusCode == OK.getStatusCode()) {
                return gatewayResponse;
            } else {
                logger.error("Gateway returned unexpected status code: {}, for gateway url={} with type {}", statusCode, url, account.getType());
                incrementFailureCounter(metricRegistry, metricsPrefix);
                throw new GatewayErrorException("Unexpected HTTP status code " + statusCode + " from gateway", gatewayResponse.getEntity(), statusCode);
            }
        } catch (ProcessingException pe) {
            incrementFailureCounter(metricRegistry, metricsPrefix);
            if (pe.getCause() != null) {
                if (pe.getCause() instanceof SocketTimeoutException) {
                    logger.error(format("Connection timed out error for gateway url=%s", url), pe);
                    throw new GatewayConnectionTimeoutException("Gateway connection timeout error");
                }
            }
            logger.error(format("Exception for gateway url=%s, error message: %s", url, pe.getMessage()), pe);
            throw new GenericGatewayException(pe.getMessage());
        } catch(GatewayErrorException e) {
            throw e;
        } catch (Exception e) {
            incrementFailureCounter(metricRegistry, metricsPrefix);
            logger.error(format("Exception for gateway url=%s", url), e);
            throw new GatewayException.GenericGatewayException(e.getMessage());
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
