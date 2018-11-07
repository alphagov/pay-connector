package uk.gov.pay.connector.gateway.stripe;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

public class StripeGatewayClient {

    private final Logger logger = LoggerFactory.getLogger(StripeGatewayClient.class);

    private final Client client;
    private final MetricRegistry metricRegistry;

    public StripeGatewayClient(Client client, MetricRegistry metricRegistry) {
        this.client = client;
        this.metricRegistry = metricRegistry;
    }

    public Response postRequest(GatewayAccountEntity account, 
                                OrderRequestType requestType, 
                                URI url, 
                                String jsonPayload, 
                                String authHeaderValue) {
        String metricsPrefix = format("gateway-operations.%s.%s.%s", account.getGatewayName(), account.getType(), requestType);

        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
        try {
            logger.info("POSTing request for account '{}' with type '{}'", account.getGatewayName(), account.getType());
            Response response = client.target(url.toString())
                    .request()
                    .header(AUTHORIZATION, authHeaderValue)
                    .post(Entity.entity(jsonPayload, APPLICATION_JSON_TYPE));

            if (response.getStatusInfo().getFamily() == Response.Status.Family.SERVER_ERROR) {
                logger.error("Stripe gateway returned server error: {}, for gateway url={} with type {}", response.getStatus(), url.toString(), account.getType());
                incrementFailureCounter(metricRegistry, metricsPrefix);
                throw new WebApplicationException("Unexpected HTTP status code " + response.getStatus() + " from gateway");
            }

            return response;
        } catch (ProcessingException pe) {
            incrementFailureCounter(metricRegistry, metricsPrefix);
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
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram(metricsPrefix + ".response_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    private void incrementFailureCounter(MetricRegistry metricRegistry, String metricsPrefix) {
        metricRegistry.counter(metricsPrefix + ".failures").inc();
    }
}
