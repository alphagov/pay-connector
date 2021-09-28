package uk.gov.pay.connector.gateway.stripe.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentWithoutAuthoriseRequest;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentIntentResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;

public class StripePaymentIntentCreator {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentIntentCreator.class);

    private GatewayClient client;
    private StripeGatewayConfig stripeGatewayConfig;
    private final String frontendUrl;
    private JsonObjectMapper jsonObjectMapper;

    public StripePaymentIntentCreator(GatewayClient client,
                                      StripeGatewayConfig stripeGatewayConfig,
                                      ConnectorConfiguration configuration,
                                      JsonObjectMapper jsonObjectMapper) {

        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.frontendUrl = configuration.getLinks().getFrontendUrl();
        this.jsonObjectMapper = jsonObjectMapper;
    }
    
    public StripePaymentIntentResponse createPaymentIntent(ChargeEntity chargeEntity) {
        try {
            String jsonResponse = client.postRequestFor(StripePaymentIntentWithoutAuthoriseRequest.of(chargeEntity, stripeGatewayConfig)).getEntity();
            return jsonObjectMapper.getObject(jsonResponse, StripePaymentIntentResponse.class);
        } catch (GatewayException.GatewayErrorException e) {
            logger.info("Error when creating Stripe payment intent status={}, response={}",
                    e.getStatus(), e.getResponseFromGateway());
            throw new RuntimeException("Error creating Stripe payment intent");
        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {
            logger.error("Error creating Stripe payment intent, error:\n {}", e.getMessage());
            throw new RuntimeException("Error creating Stripe payment intent");
        }
    } 
}
