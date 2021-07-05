package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayException.GenericGatewayException;
import uk.gov.pay.connector.gateway.model.request.GatewayClientRequest;
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
import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static javax.ws.rs.core.Response.Status.Family.familyOf;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

public class GatewayClient {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayClient.class);

    private final Client client;
    private final MetricRegistry metricRegistry;

    public GatewayClient(Client client, MetricRegistry metricRegistry) {
        this.client = client;
        this.metricRegistry = metricRegistry;
    }
    
    public GatewayClient.Response postRequestFor(URI url, PaymentGatewayName gatewayName, String gatewayAccountType, GatewayOrder request, Map<String, String> headers)
            throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayConnectionTimeoutException {
        return postRequestFor(url, gatewayName.getName(), gatewayAccountType, request, emptyList(), headers);
    }

    public GatewayClient.Response postRequestFor(GatewayClientRequest request)
            throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayConnectionTimeoutException {
        return postRequestFor(request.getUrl(), request.getGatewayAccount().getGatewayName(), request.getGatewayAccount().getType(), request.getGatewayOrder(), emptyList(), request.getHeaders());
    }

    public GatewayClient.Response postRequestFor(URI url,
                                                 GatewayAccountEntity account,
                                                 GatewayOrder request,
                                                 List<HttpCookie> cookies,
                                                 Map<String, String> headers)
            throws GatewayException.GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {
        return postRequestFor(url, account.getGatewayName(), account.getType(), request, cookies, headers);
    }

    public GatewayClient.Response postRequestFor(URI url,
                                                 String gatewayName,
                                                 String gatewayAccountType,
                                                 GatewayOrder request,
                                                 List<HttpCookie> cookies,
                                                 Map<String, String> headers)
            throws GatewayException.GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {

        String metricsPrefix = format("gateway-operations.%s.%s.%s", gatewayName, gatewayAccountType, request.getOrderRequestType());
        javax.ws.rs.core.Response response = null;

        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
        try {
            LOGGER.info("POSTing request for account '{}' with type '{}'", gatewayName, gatewayAccountType);

            Builder requestBuilder = client.target(url).request();
            headers.keySet().forEach(headerKey -> requestBuilder.header(headerKey, headers.get(headerKey)));
            cookies.forEach(cookie -> requestBuilder.header("Cookie", cookie.getName() + "=" + cookie.getValue()));
            response = requestBuilder.post(Entity.entity(request.getPayload(), request.getMediaType()));
            int statusCode = response.getStatus();
            Response gatewayResponse = new Response(response);
            if (familyOf(statusCode) == SUCCESSFUL) {
                return gatewayResponse;
            } else {
                if (statusCode >= INTERNAL_SERVER_ERROR.getStatusCode()) {
                    LOGGER.warn("Gateway returned unexpected status code: {}, for gateway url={} with type {} with order request type {}",
                            statusCode, url, gatewayAccountType, request.getOrderRequestType());
                    incrementFailureCounter(metricRegistry, metricsPrefix);
                } else {
                    LOGGER.warn("Gateway returned non-success status code: {}, for gateway url={} with type {} with order request type {}",
                            statusCode, url, gatewayAccountType, request.getOrderRequestType());
                }
                throw new GatewayErrorException("Non-success HTTP status code " + statusCode + " from gateway", gatewayResponse.getEntity(), statusCode);
            }
        } catch (ProcessingException pe) {
            incrementFailureCounter(metricRegistry, metricsPrefix);
            if (pe.getCause() != null) {
                if (pe.getCause() instanceof SocketTimeoutException) {
                    LOGGER.warn(format("Connection timed out error for gateway url=%s", url), pe);
                    throw new GatewayConnectionTimeoutException("Gateway connection timeout error");
                }
            }
            LOGGER.warn(format("Exception for gateway url=%s, error message: %s", url, pe.getMessage()), pe);
            throw new GenericGatewayException(pe.getMessage());
        } catch (GatewayErrorException e) {
            throw e;
        } catch (Exception e) {
            incrementFailureCounter(metricRegistry, metricsPrefix);
            LOGGER.error(format("Exception for gateway url=%s", url), e);
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
            delegate.getCookies().forEach((name, cookie) -> responseCookies.put(name, cookie.getValue()));
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
