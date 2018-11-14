package uk.gov.pay.connector.gateway.stripe;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;

/**
 * This class, while named StripeGatewayClient, is meant to be payment provider agnostic. It will be used by all
 * payment provider implementations after some refactoring.
 */
public class StripeGatewayClient {

    private final Logger logger = LoggerFactory.getLogger(StripeGatewayClient.class);

    private final Client client;
    private final MetricRegistry metricRegistry;

    public StripeGatewayClient(Client client, MetricRegistry metricRegistry) {
        this.client = client;
        this.metricRegistry = metricRegistry;
    }

    public Response postRequest(URI url,
                                String payload,
                                Map<String, String> headers,
                                MediaType mediaType,
                                String metricsPrefix) throws GatewayClientException {
        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
        try {
            Response response = client.target(url.toString())
                    .request()
                    .headers(new MultivaluedHashMap<>(headers))
                    .post(Entity.entity(payload, mediaType));
            
            throwIfErrorResponse(response, metricsPrefix);
            
            return response;
        } catch (ProcessingException pe) {
            metricRegistry.counter(metricsPrefix + ".failures").inc();
            if (pe.getCause() != null) {
                if (pe.getCause() instanceof UnknownHostException) {
                    logger.error(format("DNS resolution error for gateway url=%s", url.toString()), pe);
                    throw new WebApplicationException("Gateway Url DNS resolution error");
                }
                if (pe.getCause() instanceof SocketTimeoutException) {
                    logger.error(format("Connection timed out error for gateway url=%s", url.toString()), pe);
                    throw new WebApplicationException("Gateway connection timeout error");
                }
                if (pe.getCause() instanceof SocketException) {
                    logger.error(format("Socket Exception for gateway url=%s", url.toString()), pe);
                    throw new WebApplicationException("Gateway connection socket error");
                }
            }
            logger.error(format("Exception for gateway url=%s", url.toString()), pe);
            throw new WebApplicationException(pe.getMessage());
        } catch (Exception e) {
            metricRegistry.counter(metricsPrefix + ".failures").inc();
            logger.error(format("Exception for gateway url=%s", url.toString()), e);
            throw new WebApplicationException(e.getMessage());
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram(metricsPrefix + ".response_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    private void throwIfErrorResponse(Response response, String metricsPrefix) throws GatewayClientException {
        if (asList(CLIENT_ERROR, SERVER_ERROR).contains(response.getStatusInfo().getFamily())) {
            metricRegistry.counter(metricsPrefix + ".failures").inc();
            throw new GatewayClientException(
                    "Unexpected HTTP status code " + response.getStatus() + " from gateway",
                    response);
        }
    }
}
