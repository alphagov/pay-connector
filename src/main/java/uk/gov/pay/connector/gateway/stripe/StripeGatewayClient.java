package uk.gov.pay.connector.gateway.stripe;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
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
                                String metricsPrefix) throws GatewayClientException, GatewayException {
        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();

        Response response;
        try {

            Invocation.Builder clientBuilder = client.target(url.toString()).request();
            headers.keySet().forEach(headerKey -> clientBuilder.header(headerKey, headers.get(headerKey)));

            response = clientBuilder
                    .post(Entity.entity(payload, mediaType));

        } catch (Exception e) {
            metricRegistry.counter(metricsPrefix + ".failures").inc();
            logger.error(format("Exception for gateway url=%s", url.toString()), e);
            throw new GatewayException(url.toString(), e);
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram(metricsPrefix + ".response_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
        }

        throwIfErrorResponse(response, metricsPrefix);
        return response;
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
