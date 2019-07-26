package uk.gov.pay.connector.gateway.stripe.handler;

import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.AuthoriseHandler;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.Stripe3dsSourceAuthorisationResponse;
import uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeAuthorisationFailedResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeCharge;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeSourcesResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeTokenResponse;
import uk.gov.pay.connector.gateway.stripe.request.StripeAuthoriseRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentMethodRequest;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsSourceResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentIntentResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentMethodResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED;

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
            if (usePaymentIntents(request)) {
                return authoriseWithPaymentIntent(request);
            } else {
                return authoriseWithCharge(request);
            }
        } catch (GatewayException.GatewayErrorException e) {
            String chargeExternalId = request.getChargeExternalId();

            if ((e.getStatus().isPresent() && e.getStatus().get() == SC_UNAUTHORIZED) || e.getFamily() == SERVER_ERROR) {
                logger.error("Authorisation failed for charge {}. Reason: {}. Status code from Stripe: {}.",
                        chargeExternalId, e.getMessage(), e.getStatus().map(String::valueOf).orElse("no status code"));
                GatewayError gatewayError = gatewayConnectionError("There was an internal server error authorising charge_external_id: " + chargeExternalId);
                return responseBuilder.withGatewayError(gatewayError).build();
            }

            if (e.getFamily() == CLIENT_ERROR) {
                StripeErrorResponse stripeErrorResponse = jsonObjectMapper.getObject(e.getResponseFromGateway(), StripeErrorResponse.class);
                logger.error("Authorisation failed for charge {}. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
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

    private GatewayResponse authoriseWithCharge(CardAuthorisationGatewayRequest request)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();
        StripeTokenResponse tokenResponse = createToken(request);
        StripeSourcesResponse stripeSourcesResponse = createSource(request, tokenResponse.getId());
        if (stripeSourcesResponse.require3ds()) {
            String source3dsResponse = create3dsSource(request, stripeSourcesResponse.getId());
            Stripe3dsSourceResponse sourceResponse = jsonObjectMapper.getObject(source3dsResponse, Stripe3dsSourceResponse.class);

            Stripe3dsSourceAuthorisationResponse response = new Stripe3dsSourceAuthorisationResponse(sourceResponse);

            if(AUTHORISED.equals(response.authoriseStatus())){
                StripeAuthorisationResponse stripeAuthResponse = createCharge(request, response.getTransactionId());
                return responseBuilder.withResponse(stripeAuthResponse).build();
            }

            return responseBuilder.withResponse(new Stripe3dsSourceAuthorisationResponse(sourceResponse)).build();
        } else {
            StripeAuthorisationResponse stripeAuthResponse = createCharge(request, stripeSourcesResponse.getId());
            return responseBuilder.withResponse(stripeAuthResponse).build();
        }
    }

    private boolean usePaymentIntents(CardAuthorisationGatewayRequest request) {
        return request.getGatewayAccount().getIntegrationVersion3ds() == 2;
    }

    private String create3dsSource(CardAuthorisationGatewayRequest request, String sourceId)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        return postToStripe(
                "/v1/sources",
                threeDSecurePayload(request, sourceId),
                gatewayAccount.isLive(),
                gatewayAccount,
                OrderRequestType.STRIPE_CREATE_3DS_SOURCE).getEntity();
    }

    private StripeAuthorisationResponse createCharge(CardAuthorisationGatewayRequest request, String sourceId)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripeAuthoriseRequest.of(sourceId, request, stripeGatewayConfig)).getEntity();
        final StripeCharge createChargeResponse = jsonObjectMapper.getObject(jsonResponse, StripeCharge.class);
        return StripeAuthorisationResponse.of(createChargeResponse);
    }

    private StripeSourcesResponse createSource(CardAuthorisationGatewayRequest request, String tokenId)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        String jsonResponse = postToStripe(
                "/v1/sources",
                sourcesPayload(tokenId),
                gatewayAccount.isLive(),
                gatewayAccount,
                OrderRequestType.STRIPE_CREATE_SOURCE).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripeSourcesResponse.class);
    }

    private StripeTokenResponse createToken(CardAuthorisationGatewayRequest request)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        String jsonResponse = postToStripe(
                "/v1/tokens",
                tokenPayload(request),
                gatewayAccount.isLive(),
                gatewayAccount,
                OrderRequestType.STRIPE_TOKEN).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripeTokenResponse.class);
    }

    private GatewayClient.Response postToStripe(String path,
                                                String payload,
                                                boolean isLiveAccount,
                                                GatewayAccountEntity gatewayAccountEntity,
                                                OrderRequestType orderRequestType)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        return client.postRequestFor(
                URI.create(stripeGatewayConfig.getUrl() + path),
                gatewayAccountEntity,
                new GatewayOrder(orderRequestType, payload, APPLICATION_FORM_URLENCODED_TYPE),
                AuthUtil.getStripeAuthHeader(stripeGatewayConfig, isLiveAccount)
        );
    }

    private String sourcesPayload(String token) {
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("type", "card"));
        params.add(new BasicNameValuePair("token", token));
        params.add(new BasicNameValuePair("usage", "single_use"));
        return URLEncodedUtils.format(params, UTF_8);
    }

    private String threeDSecurePayload(CardAuthorisationGatewayRequest request, String sourceId) {
        String frontend3dsIncomingUrl = String.format("%s/card_details/%s/3ds_required_in", frontendUrl, request.getChargeExternalId());
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("type", "three_d_secure"));
        params.add(new BasicNameValuePair("amount", request.getAmount()));
        params.add(new BasicNameValuePair("currency", "GBP"));
        params.add(new BasicNameValuePair("redirect[return_url]", frontend3dsIncomingUrl));
        params.add(new BasicNameValuePair("three_d_secure[card]", sourceId));
        return URLEncodedUtils.format(params, UTF_8);
    }

    private String tokenPayload(CardAuthorisationGatewayRequest request) {
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("card[cvc]", request.getAuthCardDetails().getCvc()));
        params.add(new BasicNameValuePair("card[exp_month]", request.getAuthCardDetails().expiryMonth()));
        params.add(new BasicNameValuePair("card[exp_year]", request.getAuthCardDetails().expiryYear()));
        params.add(new BasicNameValuePair("card[number]", request.getAuthCardDetails().getCardNo()));
        params.add(new BasicNameValuePair("card[name]", request.getAuthCardDetails().getCardHolder()));

        request.getAuthCardDetails().getAddress().ifPresent(address -> {
            params.add(new BasicNameValuePair("card[address_line1]", address.getLine1()));

            if (StringUtils.isNotBlank(address.getLine2())) {
                params.add(new BasicNameValuePair("card[address_line2]", address.getLine2()));
            }

            params.add(new BasicNameValuePair("card[address_city]", address.getCity()));
            params.add(new BasicNameValuePair("card[address_country]", address.getCountry()));
            params.add(new BasicNameValuePair("card[address_zip]", address.getPostcode()));
        });

        return URLEncodedUtils.format(params, UTF_8);
    }

    private GatewayResponse authoriseWithPaymentIntent(CardAuthorisationGatewayRequest request)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        StripePaymentMethodResponse stripePaymentMethodResponse = createPaymentMethod(request);
        StripePaymentIntentResponse stripePaymentIntentResponse = createPaymentIntent(request, stripePaymentMethodResponse.getId());

        return GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder()
                .withResponse(StripeAuthorisationResponse.of(stripePaymentIntentResponse)).build();
    }

    private StripePaymentIntentResponse createPaymentIntent(CardAuthorisationGatewayRequest request, String paymentMethodId)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripePaymentIntentRequest.of(request, paymentMethodId, stripeGatewayConfig, frontendUrl)).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentIntentResponse.class);
    }

    private StripePaymentMethodResponse createPaymentMethod(CardAuthorisationGatewayRequest request)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripePaymentMethodRequest.of(request, stripeGatewayConfig)).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentMethodResponse.class);
    }
}
