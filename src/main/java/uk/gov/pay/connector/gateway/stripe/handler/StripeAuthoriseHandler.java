package uk.gov.pay.connector.gateway.stripe.handler;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.AuthoriseHandler;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeAuthorisationFailedResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeCustomer;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripePaymentIntent;
import uk.gov.pay.connector.gateway.stripe.json.StripePaymentMethod;
import uk.gov.pay.connector.gateway.stripe.json.StripeToken;
import uk.gov.pay.connector.gateway.stripe.request.StripeCustomerRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentMethodRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTokenRequest;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;
import uk.gov.service.payments.commons.api.exception.ValidationException;

import java.util.List;

import static jakarta.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static jakarta.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY;

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
        if (request.isSavePaymentInstrumentToAgreement()) {
            logger.info("Calling Stripe for authorisation of charge to set up agreement");
        } else {
            logger.info("Calling Stripe for authorisation of charge");
        }
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();
        try {
            StripePaymentMethod stripePaymentMethodResponse = createPaymentMethod(request);

            StripePaymentIntent stripePaymentIntent;
            if (request.isSavePaymentInstrumentToAgreement()) {
                StripeCustomer stripeCustomerResponse = createCustomer(request, request.getAgreement().orElseThrow(() -> new RuntimeException("Expected charge with isSavePaymentInstrumentToAgreement == true to have a saved agreement")));
                var customerId = stripeCustomerResponse.getId();
                stripePaymentIntent = createPaymentIntentForSetUpAgreement(request, stripePaymentMethodResponse.getId(), customerId);
                logger.info("Created Stripe payment intent and stored payment details for recurring payment agreement",
                        kv("stripe_payment_intent_id", stripePaymentIntent.getId()));
            } else {
                stripePaymentIntent = createPaymentIntent(request, stripePaymentMethodResponse.getId());
                logger.info("Created Stripe payment intent",
                        kv("stripe_payment_intent_id", stripePaymentIntent.getId()));
            }

            return responseBuilder.withResponse(StripeAuthorisationResponse.of(stripePaymentIntent)).build();
        } catch (GatewayException.GatewayErrorException e) {
            return handleGatewayError(responseBuilder, e);
        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {
            logger.error("GatewayException occurred, error:\n {}", e);
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }

    public GatewayResponse authoriseUserNotPresent(RecurringPaymentAuthorisationGatewayRequest request) {
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();
        try {
            var StripePaymentIntent = createPaymentIntentForUserNotPresent(request);
            return responseBuilder.withResponse(StripeAuthorisationResponse.of(StripePaymentIntent)).build();
        } catch (GatewayException.GatewayErrorException e) {
            return handleGatewayError(responseBuilder, e);
        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {
            logger.error("GatewayException occurred, error:\n {}", e);
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }

    public GatewayResponse authoriseApplePay(ApplePayAuthorisationGatewayRequest request) {
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();
        try {
            StripeToken stripeTokenResponse = createTokenForApplePay(request);
            StripePaymentIntent StripePaymentIntent = createPaymentIntentFromWalletToken(request, stripeTokenResponse.getId());
            return responseBuilder
                    .withResponse(StripeAuthorisationResponse.of(StripePaymentIntent)).build();
        } catch (GatewayException.GatewayErrorException e) {
            return handleGatewayError(responseBuilder, e);
        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {
            logger.error("GatewayException occurred, error:\n {}", e);
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }

    public GatewayResponse authoriseGooglePay(GooglePayAuthorisationGatewayRequest request) {
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();
        try {
            String tokenId = request.getGooglePayAuthRequest().getTokenId().orElseThrow(() -> new ValidationException(List.of("Field [token_id] is required")));
            StripePaymentIntent StripePaymentIntent = createPaymentIntentFromWalletToken(request, tokenId);
            return responseBuilder
                    .withResponse(StripeAuthorisationResponse.of(StripePaymentIntent)).build();
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
            StripeErrorResponse stripeErrorResponse = jsonObjectMapper.getObject(e.getResponseFromGateway(), StripeErrorResponse.class);
            logger.info("Authorisation failed. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
                    stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), e.getStatus());

            return responseBuilder.withResponse(StripeAuthorisationFailedResponse.of(stripeErrorResponse)).build();
        }

        logger.info("Unrecognised response status when authorising - status={}, response={}",
                e.getStatus(), e.getResponseFromGateway());
        throw new RuntimeException("Unrecognised response status when authorising.");
    }

    private StripeCustomer createCustomer(CardAuthorisationGatewayRequest request, AgreementEntity agreement)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripeCustomerRequest.of(request, stripeGatewayConfig, agreement)).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripeCustomer.class);
    }

    private StripePaymentMethod createPaymentMethod(CardAuthorisationGatewayRequest request)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripePaymentMethodRequest.of(request, stripeGatewayConfig)).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentMethod.class);
    }

    private StripePaymentIntent createPaymentIntent(CardAuthorisationGatewayRequest request, String paymentMethodId)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        StripePaymentIntentRequest paymentIntentRequest = StripePaymentIntentRequest.createOneOffPaymentIntentRequest(
                request, paymentMethodId, stripeGatewayConfig, frontendUrl);
        String jsonResponse = client.postRequestFor(paymentIntentRequest).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentIntent.class);
    }

    private StripePaymentIntent createPaymentIntentForSetUpAgreement(CardAuthorisationGatewayRequest request, String paymentMethodId, String customerId)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        var paymentIntentRequest = StripePaymentIntentRequest.createPaymentIntentRequestWithSetupFutureUsage(
                request, paymentMethodId, customerId, stripeGatewayConfig, frontendUrl);
        String jsonResponse = client.postRequestFor(paymentIntentRequest).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentIntent.class);
    }

    private StripePaymentIntent createPaymentIntentForUserNotPresent(RecurringPaymentAuthorisationGatewayRequest request)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        var paymentInstrument = request.getPaymentInstrument().orElseThrow(() -> new IllegalArgumentException("Expected request to have payment instrument but it does not"));
        var recurringAuthToken = paymentInstrument.getRecurringAuthToken().orElseThrow(() -> new IllegalArgumentException("Payment instrument does not have recurring auth token set"));

        var customerId = recurringAuthToken.get(STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY);
        var paymentMethodId = recurringAuthToken.get(STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY);
        var paymentIntentRequest = StripePaymentIntentRequest.createPaymentIntentRequestUseSavedPaymentDetails(
                request, paymentMethodId, customerId, stripeGatewayConfig, frontendUrl);
        String jsonResponse = client.postRequestFor(paymentIntentRequest).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentIntent.class);
    }

    private StripeToken createTokenForApplePay(ApplePayAuthorisationGatewayRequest authorisationGatewayRequest) 
            throws GatewayException.GenericGatewayException, GatewayException.GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        String tokenJsonResponse = client.postRequestFor(StripeTokenRequest.of(authorisationGatewayRequest, stripeGatewayConfig)).getEntity();
        return jsonObjectMapper.getObject(tokenJsonResponse, StripeToken.class);
    }

    private StripePaymentIntent createPaymentIntentFromWalletToken(AuthorisationGatewayRequest request, String tokenId) 
            throws GatewayException.GenericGatewayException, GatewayException.GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        String paymentIntentJsonResponse = client.postRequestFor(StripePaymentIntentRequest.createPaymentIntentRequestWithToken(request, tokenId, stripeGatewayConfig, frontendUrl)).getEntity();
        return jsonObjectMapper.getObject(paymentIntentJsonResponse, StripePaymentIntent.class);
    }
}
