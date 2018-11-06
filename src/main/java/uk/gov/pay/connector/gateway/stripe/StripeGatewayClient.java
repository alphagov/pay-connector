package uk.gov.pay.connector.gateway.stripe;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.ConnectorClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;

public class StripeGatewayClient implements ConnectorClient {

    private final Logger logger = LoggerFactory.getLogger(StripeGatewayClient.class);

    private final Client client;
    private final MetricRegistry metricRegistry;
    private StripeGatewayConfig stripeGatewayConfig;

    public StripeGatewayClient(Client client, 
                               MetricRegistry metricRegistry, 
                               StripeGatewayConfig stripeGatewayConfig) {
        this.client = client;
        this.metricRegistry = metricRegistry;
        this.stripeGatewayConfig = stripeGatewayConfig;
    }

    public Response postRequest(GatewayAccountEntity account, GatewayOrder request, String path) throws GatewayError {
        String metricsPrefix = format("gateway-operations.%s.%s.%s", account.getGatewayName(), account.getType(), request.getOrderRequestType());
        Response response = null;

        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
        String stripeUrl = stripeGatewayConfig.getUrl();
        try {
            logger.info("POSTing request for account '{}' with type '{}'", account.getGatewayName(), account.getType());
            response = client.target(stripeUrl + path)
                    .request()
                    .header(AUTHORIZATION, "Bearer " + stripeGatewayConfig.getAuthToken())
                    .post(Entity.entity(request.getPayload(), request.getMediaType()));

            if (response.getStatusInfo().getFamily() == Response.Status.Family.SERVER_ERROR) {
                logger.error("Stripe gateway returned server error: {}, for gateway url={} with type {}", response.getStatus(), stripeUrl, account.getType());
                incrementFailureCounter(metricRegistry, metricsPrefix);
                throw GatewayError.unexpectedStatusCodeFromGateway("Unexpected HTTP status code " + response.getStatus() + " from gateway");
            }

            return response;
        } catch (ProcessingException pe) {
            incrementFailureCounter(metricRegistry, metricsPrefix);
            if (pe.getCause() != null) {
                if (pe.getCause() instanceof UnknownHostException) {
                    logger.error(format("DNS resolution error for gateway url=%s", stripeUrl), pe);
                    throw GatewayError.unknownHostException("Gateway Url DNS resolution error");
                }
                if (pe.getCause() instanceof SocketTimeoutException) {
                    logger.error(format("Connection timed out error for gateway url=%s", stripeUrl), pe);
                    throw GatewayError.gatewayConnectionTimeoutException("Gateway connection timeout error");
                }
                if (pe.getCause() instanceof SocketException) {
                    logger.error(format("Socket Exception for gateway url=%s", stripeUrl), pe);
                    throw GatewayError.gatewayConnectionSocketException("Gateway connection socket error");
                }
            }
            logger.error(format("Exception for gateway url=%s", stripeUrl), pe);
            throw GatewayError.baseError(pe.getMessage());
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram(metricsPrefix + ".response_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }
}
