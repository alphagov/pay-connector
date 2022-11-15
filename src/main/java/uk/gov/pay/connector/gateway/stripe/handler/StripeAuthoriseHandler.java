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
import uk.gov.pay.connector.gateway.stripe.request.StripeCustomerRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentMethodRequest;
import uk.gov.pay.connector.gateway.stripe.response.StripeCustomerResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentIntentResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentMethodResponse;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.service.payments.commons.model.AuthorisationMode;

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
        logger.info("Calling Stripe for authorisation of charge");
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();
        try {
            String customerId = null;
            String paymentMethodId = null;
            if (request.isSavePaymentInstrumentToAgreement()) {
                // set a customer up to be stored by this agreement
                customerId = createCustomer(request).getId();
            }
            
            if (request.getAuthorisationMode() == AuthorisationMode.AGREEMENT) {
                if (request.getPaymentInstrument().isPresent()) {
                    customerId = request.getPaymentInstrument().get().getRecurringAuthToken().get("customer_id");
                    paymentMethodId = request.getPaymentInstrument().get().getRecurringAuthToken().get("payment_method_id");
                }
            } else {
                paymentMethodId = createPaymentMethod(request).getId();
                
            }
            StripePaymentIntentResponse stripePaymentIntentResponse = createPaymentIntent(request, paymentMethodId, customerId);
            
            // if we did set up a customer for recurring payments and everything auth'd appropriately, we can't to return those for the payment instrument
            // we'll need the customer id and the payment method id (both of these are likely returned in the payment intent response)
            return GatewayResponse
                    .GatewayResponseBuilder
                    .responseBuilder()
                    .withResponse(StripeAuthorisationResponse.of(stripePaymentIntentResponse)).build();
        } catch (GatewayException.GatewayErrorException e) {
            if ((e.getStatus().isPresent() && e.getStatus().get() == SC_UNAUTHORIZED) || e.getFamily() == SERVER_ERROR) {
                logger.error("Authorisation failed due to an internal error. Reason: {}. Status code from Stripe: {}.",
                        e.getMessage(), e.getStatus().map(String::valueOf).orElse("no status code"));
                GatewayError gatewayError = gatewayConnectionError("There was an internal server error authorising charge");
                return responseBuilder.withGatewayError(gatewayError).build();
            }

            if (e.getFamily() == CLIENT_ERROR) {
                StripeErrorResponse stripeErrorResponse = jsonObjectMapper.getObject(e.getResponseFromGateway(), StripeErrorResponse.class);
                logger.info("Authorisation failed. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
                        stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), e.getStatus());
    
                return responseBuilder.withResponse(StripeAuthorisationFailedResponse.of(stripeErrorResponse)).build();
            }

            logger.info("Unrecognised response status when authorising - status={}, response={}",
                    e.getStatus(), e.getResponseFromGateway());
            throw new RuntimeException("Unrecognised response status when authorising.");

        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {
            logger.error("GatewayException occurred, error:\n {}", e);
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }

    private StripeCustomerResponse createCustomer(CardAuthorisationGatewayRequest request)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripeCustomerRequest.of(request, stripeGatewayConfig)).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripeCustomerResponse.class);
    }
    
    private StripePaymentIntentResponse createPaymentIntent(CardAuthorisationGatewayRequest request, String paymentMethodId, String customerId)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripePaymentIntentRequest.of(request, paymentMethodId, customerId, stripeGatewayConfig, frontendUrl)).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentIntentResponse.class);
    }

    private StripePaymentMethodResponse createPaymentMethod(CardAuthorisationGatewayRequest request)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripePaymentMethodRequest.of(request, stripeGatewayConfig)).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentMethodResponse.class);
    }
}
