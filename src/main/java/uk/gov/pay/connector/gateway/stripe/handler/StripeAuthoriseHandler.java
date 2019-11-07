package uk.gov.pay.connector.gateway.stripe.handler;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.AuthoriseHandler;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeAuthorisationFailedResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentMethodRequest;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentIntentResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentMethodResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;

public class StripeAuthoriseHandler implements AuthoriseHandler {

    private static final Logger logger = LoggerFactory.getLogger(StripeAuthoriseHandler.class);

    private final GatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final JsonObjectMapper jsonObjectMapper;
    private final String frontendUrl;
    @Inject
    public StripeAuthoriseHandler(GatewayClient client,
                                  StripeGatewayConfig stripeGatewayConfig,
                                  ConnectorConfiguration configuration,
                                  JsonObjectMapper jsonObjectMapper) {
        this.client = client;
        this.frontendUrl = configuration.getLinks().getFrontendUrl();
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    @Override
    public GatewayResponse authorise(CardAuthorisationGatewayRequest request) {
        logger.info("Calling Stripe for authorisation of charge [{}]", request.getChargeExternalId());
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();
        try {
            StripePaymentMethodResponse stripePaymentMethodResponse = createPaymentMethod(request);
            StripePaymentIntentResponse stripePaymentIntentResponse = createPaymentIntent(request, stripePaymentMethodResponse.getId());

            return GatewayResponse
                    .GatewayResponseBuilder
                    .responseBuilder()
                    .withResponse(StripeAuthorisationResponse.of(stripePaymentIntentResponse)).build();
        } catch (GatewayException.GatewayErrorException e) {
            String chargeExternalId = request.getChargeExternalId();

            if ((e.getStatus().isPresent() && e.getStatus().get() == SC_UNAUTHORIZED) || e.getFamily() == SERVER_ERROR) {
                logger.error("Authorisation failed for charge {} due to an internal error. Reason: {}. Status code from Stripe: {}.",
                        chargeExternalId, e.getMessage(), e.getStatus().map(String::valueOf).orElse("no status code"));
                GatewayError gatewayError = gatewayConnectionError("There was an internal server error authorising charge_external_id: " + chargeExternalId);
                return responseBuilder.withGatewayError(gatewayError).build();
            }

            if (e.getFamily() == CLIENT_ERROR) {
                StripeErrorResponse stripeErrorResponse = jsonObjectMapper.getObject(e.getResponseFromGateway(), StripeErrorResponse.class);
                logger.info("Authorisation failed for charge {}. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
                        chargeExternalId, stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), e.getStatus());
    
                return responseBuilder.withResponse(StripeAuthorisationFailedResponse.of(stripeErrorResponse)).build();
            }

            logger.info("Unrecognised response status when authorising. Charge_id={}, status={}, response={}",
                    chargeExternalId, e.getStatus(), e.getResponseFromGateway());
            throw new RuntimeException("Unrecognised response status when authorising.");

        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {
            logger.error("GatewayException occurred for charge external id {}, error:\n {}", request.getChargeExternalId(), e);
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }

    private StripePaymentIntentResponse createPaymentIntent(CardAuthorisationGatewayRequest request, String paymentMethodId)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
//        boolean isMoto = request.getGatewayAccount().getId() == 182;
        String jsonResponse = client.postRequestFor(StripePaymentIntentRequest.of(request, paymentMethodId, stripeGatewayConfig, frontendUrl, true)).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentIntentResponse.class);
    }

    private StripePaymentMethodResponse createPaymentMethod(CardAuthorisationGatewayRequest request)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripePaymentMethodRequest.of(request, stripeGatewayConfig)).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentMethodResponse.class);
    }
}
