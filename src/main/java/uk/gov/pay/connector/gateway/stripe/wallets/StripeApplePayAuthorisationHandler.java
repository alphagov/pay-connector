package uk.gov.pay.connector.gateway.stripe.wallets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeAuthorisationFailedResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTokenRequest;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentIntentResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeTokenResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;

public class StripeApplePayAuthorisationHandler {

    private static final Logger logger = LoggerFactory.getLogger(StripeApplePayAuthorisationHandler.class);
    private GatewayClient client;
    private StripeGatewayConfig stripeGatewayConfig;
    private String frontendUrl;
    private JsonObjectMapper objectMapper;

    public StripeApplePayAuthorisationHandler(GatewayClient client,
                                              StripeGatewayConfig stripeGatewayConfig,
                                              ConnectorConfiguration configuration,
                                              JsonObjectMapper objectMapper) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.frontendUrl = configuration.getLinks().getFrontendUrl();
        this.objectMapper = objectMapper;
    }
    
    public GatewayResponse<BaseAuthoriseResponse> authorise(WalletAuthorisationGatewayRequest request) throws GatewayException {
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();
        try {
            String tokenJsonResponse = client.postRequestFor(StripeTokenRequest.of(request, stripeGatewayConfig)).getEntity();
            StripeTokenResponse stripeTokenResponse = objectMapper.getObject(tokenJsonResponse, StripeTokenResponse.class);
            String paymentIntentJsonResponse = client.postRequestFor(StripePaymentIntentRequest.createPaymentIntentRequestWithToken(request, stripeTokenResponse.getId(), stripeGatewayConfig, frontendUrl)).getEntity();
            StripePaymentIntentResponse stripePaymentIntentResponse = objectMapper.getObject(paymentIntentJsonResponse, StripePaymentIntentResponse.class);
            return responseBuilder
                    .withResponse(StripeAuthorisationResponse.of(stripePaymentIntentResponse)).build();
        } catch (GatewayException.GatewayErrorException e) {
            return handleGatewayError(responseBuilder, e);
        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {
            logger.error("GatewayException occurred, error:\n {}", e);
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }

    private GatewayResponse handleGatewayError(GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder, GatewayException.GatewayErrorException e) {
        if ((e.getStatus().isPresent() && e.getStatus().get() == SC_UNAUTHORIZED) || e.getFamily() == SERVER_ERROR) {
            logger.error("Authorisation failed due to an internal error. Reason: {}. Status code from Stripe: {}.",
                    e.getMessage(), e.getStatus().map(String::valueOf).orElse("no status code"));
            GatewayError gatewayError = gatewayConnectionError("There was an internal server error authorising charge");
            return responseBuilder.withGatewayError(gatewayError).build();
        }

        if (e.getFamily() == CLIENT_ERROR) {
            StripeErrorResponse stripeErrorResponse = objectMapper.getObject(e.getResponseFromGateway(), StripeErrorResponse.class);
            logger.info("Authorisation failed. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
                    stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), e.getStatus());

            return responseBuilder.withResponse(StripeAuthorisationFailedResponse.of(stripeErrorResponse)).build();
        }

        logger.info("Unrecognised response status when authorising - status={}, response={}",
                e.getStatus(), e.getResponseFromGateway());
        throw new RuntimeException("Unrecognised response status when authorising.");
    }
}
