package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayException.GenericGatewayException;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.GatewayClientGetRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import java.net.HttpCookie;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static javax.ws.rs.core.Response.Status.Family.familyOf;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

public class GatewayClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayClient.class);

    private final Client client;
    private final MetricRegistry metricRegistry;

    private static final Counter gatewayOperationsFailures = Counter.build()
        .name("gateway_operations_failures_total")
        .help("Number of failed gateway operations")
        .labelNames("gatewayName", "gatewayAccountType", "requestType")
        .register();
    private static final Histogram gatewayOperationsResponseTime = Histogram.build()
        .name("gateway_operations_response_time_seconds")
        .help("Response times for gateway operations in seconds")
        .labelNames("gatewayName", "gatewayAccountType", "requestType")
        .register();

    public GatewayClient(Client client, MetricRegistry metricRegistry) {
        this.client = client;
        this.metricRegistry = metricRegistry;
    }

    public GatewayClient.Response postRequestFor(URI url, PaymentGatewayName gatewayName, String gatewayAccountType, GatewayOrder request, Map<String, String> headers)
            throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayConnectionTimeoutException {
        return postRequestFor(url, gatewayName, gatewayAccountType, request, emptyList(), headers);
    }

    public GatewayClient.Response postRequestFor(GatewayClientPostRequest request)
            throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayConnectionTimeoutException {
        return postRequestFor(request.getUrl(), request.getPaymentProvider(), request.getGatewayAccountType(), request.getGatewayOrder(), emptyList(), request.getHeaders());
    }

    public GatewayClient.Response postRequestFor(URI url,
                                                 PaymentGatewayName gatewayName,
                                                 String gatewayAccountType,
                                                 GatewayOrder request,
                                                 List<HttpCookie> cookies,
                                                 Map<String, String> headers)
            throws GatewayException.GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {

        String metricsPrefix = format("gateway-operations.%s.%s.%s", gatewayName.getName(), gatewayAccountType, request.getOrderRequestType());

        Supplier<javax.ws.rs.core.Response> requestCallable = () -> {
            LOGGER.info("POSTing request for account '{}' with type '{}'", gatewayName.getName(), gatewayAccountType);

            Builder requestBuilder = client.target(url).request();
            headers.keySet().forEach(headerKey -> requestBuilder.header(headerKey, headers.get(headerKey)));
            cookies.forEach(cookie -> requestBuilder.header("Cookie", cookie.getName() + "=" + cookie.getValue()));
            return requestBuilder.post(Entity.entity(request.getPayload(), request.getMediaType()));
        };
        return executeRequest(url, gatewayName, gatewayAccountType, request.getOrderRequestType(), metricsPrefix, requestCallable);
    }

    public GatewayClient.Response getRequestFor(GatewayClientGetRequest request)
            throws GatewayException.GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {
        return getRequestFor(request.getUrl(), request.getPaymentProvider(), request.getGatewayAccountType(),
                request.getOrderRequestType(), emptyList(), request.getHeaders(), request.getQueryParams());
    }

    public GatewayClient.Response getRequestFor(URI url,
                                                PaymentGatewayName gatewayName,
                                                String gatewayAccountType,
                                                OrderRequestType orderRequestType,
                                                List<HttpCookie> cookies,
                                                Map<String, String> headers,
                                                Map<String, String> queryParams)
            throws GatewayException.GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {
        
        String metricsPrefix = format("gateway-operations.get.%s.%s.%s", gatewayName.getName(), gatewayAccountType, orderRequestType);

        Supplier<javax.ws.rs.core.Response> requestCallable = () -> {
            LOGGER.info("Making GET request for account '{}' with type '{}'", gatewayName.getName(), gatewayAccountType);

            WebTarget target = client.target(url);
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                target = target.queryParam(entry.getKey(), entry.getValue());
            }
            Builder requestBuilder = target.request();
            headers.keySet().forEach(headerKey -> requestBuilder.header(headerKey, headers.get(headerKey)));
            cookies.forEach(cookie -> requestBuilder.header("Cookie", cookie.getName() + "=" + cookie.getValue()));
            return requestBuilder.get();
        };

        return executeRequest(url, gatewayName, gatewayAccountType, orderRequestType, metricsPrefix, requestCallable);
    }

    private GatewayClient.Response executeRequest(URI url,
                                                  PaymentGatewayName gatewayName,
                                                  String gatewayAccountType,
                                                  OrderRequestType orderRequestType,
                                                  String metricsPrefix,
                                                  Supplier<javax.ws.rs.core.Response> requestCallable)
            throws GatewayException.GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {
        javax.ws.rs.core.Response response = null;

        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
        Histogram.Timer responseTimeTimer = gatewayOperationsResponseTime.labels(
                gatewayName.toString().toLowerCase(),
                gatewayAccountType.toLowerCase(),
                orderRequestType.toString().toLowerCase()
        ).startTimer();

        try {
            response = requestCallable.get();
            int statusCode = response.getStatus();
            Response gatewayResponse = new Response(response);
            if (familyOf(statusCode) == SUCCESSFUL) {
                return gatewayResponse;
            } else {
                if (statusCode >= INTERNAL_SERVER_ERROR.getStatusCode()) {
                    LOGGER.warn("Gateway returned unexpected status code: {}, for gateway url={} with type {} with order request type {}",
                            statusCode, url, gatewayAccountType, orderRequestType);
                    incrementFailureCounter(metricRegistry, metricsPrefix);
                    incrementPrometheusFailureCounter(gatewayName, gatewayAccountType, orderRequestType);
                } else {
                    LOGGER.warn("Gateway returned non-success status code: {}, for gateway url={} with type {} with order request type {}",
                            statusCode, url, gatewayAccountType, orderRequestType);
                }
                throw new GatewayErrorException("Non-success HTTP status code " + statusCode + " from gateway", gatewayResponse.getEntity(), statusCode);
            }
        } catch (ProcessingException pe) {
            incrementFailureCounter(metricRegistry, metricsPrefix);
            incrementPrometheusFailureCounter(gatewayName, gatewayAccountType, orderRequestType);
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
            incrementPrometheusFailureCounter(gatewayName, gatewayAccountType, orderRequestType);
            LOGGER.error(format("Exception for gateway url=%s", url), e);
            throw new GatewayException.GenericGatewayException(e.getMessage());
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram(metricsPrefix + ".response_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
            responseTimeTimer.observeDuration();
            if (response != null) {
                response.close();
            }
        }
    }

    private void incrementFailureCounter(MetricRegistry metricRegistry, String metricsPrefix) {
        metricRegistry.counter(metricsPrefix + ".failures").inc();
    }

    private void incrementPrometheusFailureCounter(PaymentGatewayName gatewayName, String gatewayAccountType, OrderRequestType orderRequestType) {
        this.gatewayOperationsFailures.labels(
                gatewayName.toString().toLowerCase(),
                gatewayAccountType.toLowerCase(),
                orderRequestType.toString().toLowerCase()
        ).inc();
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
