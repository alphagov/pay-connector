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
                                String metricsPrefix) throws GatewayClientException, GatewayException, DownstreamException {
        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();

        Response response;
        try {
            logger.info("POSTing request for gateway url={}", url);
            Invocation.Builder clientBuilder = client.target(url.toString()).request();
            headers.keySet().forEach(headerKey -> clientBuilder.header(headerKey, headers.get(headerKey)));

            response = clientBuilder.post(Entity.entity(payload, mediaType));

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

    private void throwIfErrorResponse(Response response, String metricsPrefix) throws GatewayClientException, DownstreamException {

        switch (response.getStatusInfo().getFamily()) {

            case SUCCESSFUL:
                break;

            case CLIENT_ERROR:
                metricRegistry.counter(metricsPrefix + ".failures").inc();
                throw new GatewayClientException("Unexpected HTTP status code " + response.getStatus() + " from gateway", response);

            case SERVER_ERROR:
                metricRegistry.counter(metricsPrefix + ".failures").inc();
                String responseMessage = response.readEntity(String.class);
                logger.error("Unexpected HTTP status code {} from gateway, error=[{}]", response.getStatus(), responseMessage);
                throw new DownstreamException(response.getStatus(), responseMessage);

             default:
                 logger.error("Other HTTP status from gateway=[stripe], response=[]", response);
        }
    }
}
