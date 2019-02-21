package uk.gov.pay.connector.gateway.stripe;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCancelHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCaptureHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeRefundHandler;
import uk.gov.pay.connector.gateway.stripe.json.StripeCreateChargeResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeSourcesResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeTokenResponse;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsSourceResponse;
import uk.gov.pay.connector.gateway.stripe.util.StripeAuthUtil;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED;

@Singleton
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentProvider.class);

    private final StripeGatewayClient client;
    private final String frontendUrl;
    private final JsonObjectMapper jsonObjectMapper;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final StripeCaptureHandler stripeCaptureHandler;
    private final StripeCancelHandler stripeCancelHandler;
    private final StripeRefundHandler stripeRefundHandler;

    @Inject
    public StripePaymentProvider(StripeGatewayClient stripeGatewayClient,
                                 ConnectorConfiguration configuration,
                                 JsonObjectMapper jsonObjectMapper) {
        this.stripeGatewayConfig = configuration.getStripeConfig();
        this.client = stripeGatewayClient;
        this.frontendUrl = configuration.getLinks().getFrontendUrl();
        this.jsonObjectMapper = jsonObjectMapper;
        this.externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        stripeCaptureHandler = new StripeCaptureHandler(client, stripeGatewayConfig, jsonObjectMapper);
        stripeCancelHandler = new StripeCancelHandler(client, stripeGatewayConfig, jsonObjectMapper);
        stripeRefundHandler = new StripeRefundHandler(client, stripeGatewayConfig, jsonObjectMapper);
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return STRIPE;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    @Override
    public ChargeQueryResponse queryPaymentStatus(ChargeEntity charge) {
        throw new UnsupportedOperationException("Querying payment status not currently supported by Stripe");
    }

    @Override
    public GatewayResponse authorise(CardAuthorisationGatewayRequest request) {
        logger.info("Calling Stripe for authorisation of charge [{}]", request.getChargeExternalId());

        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();

        try {
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
        } catch (GatewayClientException e) {

            StripeGatewayClientResponse response = e.getResponse();

            if (response.getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                return unexpectedGatewayResponse(request.getChargeExternalId(), responseBuilder, e.getMessage(), HttpStatus.SC_UNAUTHORIZED);
            }

            StripeErrorResponse stripeErrorResponse = jsonObjectMapper.getObject(response.getPayload(), StripeErrorResponse.class);
            logger.error("Authorisation failed for charge {}. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
                    request.getChargeExternalId(), stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), response.getStatus());
            GatewayError gatewayError = genericGatewayError(stripeErrorResponse.getError().getMessage());

            return responseBuilder.withGatewayError(gatewayError).build();

        } catch (DownstreamException e) {
            return unexpectedGatewayResponse(request.getChargeExternalId(), responseBuilder, e.getMessage(), e.getStatusCode());
        } catch (GatewayException e) {
            logger.error("GatewayException occurred for charge external id {}, error:\n {}", request.getChargeExternalId(), e);
            return responseBuilder.withGatewayError(GatewayError.of(e)).build();
        }
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {

        if (request.getAuth3DsDetails() != null && request.getAuth3DsDetails().getAuth3DsResult() != null) {

            switch (request.getAuth3DsDetails().getAuth3DsResult()) {
                case CANCELED:
                    return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.CANCELLED);
                case ERROR:
                    return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.ERROR);
                case DECLINED:
                    return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED);
                case AUTHORISED:
                    return authorise3DSSource(request);
            }
        }

        // if Auth3DSResult is not available, return response as AUTH_3DS_READY
        // (to keep the transaction in auth waiting until a notification is received from Stripe for 3DS authorisation)
        return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.AUTH_3DS_READY);
    }

    private Gateway3DSAuthorisationResponse authorise3DSSource(Auth3dsResponseGatewayRequest request) {
        try {
            StripeAuthorisationResponse stripeAuthResponse = createChargeFor3DSSource(request, request.getTransactionId().get());
            return Gateway3DSAuthorisationResponse.of(stripeAuthResponse.authoriseStatus(), stripeAuthResponse.getTransactionId());
        } catch (GatewayClientException e) {

            StripeGatewayClientResponse response = e.getResponse();

            if (response.getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.ERROR);
            }

            StripeErrorResponse stripeErrorResponse = jsonObjectMapper.getObject(response.getPayload(), StripeErrorResponse.class);
            logger.error("3DS Authorisation failed for charge {}. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
                    request.getChargeExternalId(), stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), response.getStatus());

            return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.REJECTED);

        } catch (DownstreamException | GatewayException e) {
            return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION);
        }
    }

    private GatewayResponse unexpectedGatewayResponse(String chargeExternalId,
                                                      GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder,
                                                      String errorMessage,
                                                      int statusCode) {
        logger.error("Authorisation failed for charge {}. Reason: {}. Status code from Stripe: {}.",
                chargeExternalId, errorMessage, statusCode);
        GatewayError gatewayError = gatewayConnectionError("There was an internal server error authorising charge_external_id: " + chargeExternalId);
        return responseBuilder.withGatewayError(gatewayError).build();
    }

    private String create3dsSource(CardAuthorisationGatewayRequest request, String sourceId)
            throws GatewayClientException, GatewayException, DownstreamException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        return postToStripe(
                "/v1/sources",
                threeDSecurePayload(request, sourceId),
                format("gateway-operations.%s.%s.authorise.create_3ds_source",
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType()),
                gatewayAccount.isLive());
    }

    private StripeAuthorisationResponse createChargeFor3DSSource(Auth3dsResponseGatewayRequest request, String sourceId)
            throws GatewayClientException, GatewayException, DownstreamException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        String jsonResponse = postToStripe(
                "/v1/charges",
                authorise3DSChargePayload(request, sourceId),
                format("gateway-operations.%s.%s.authorise.create_charge",
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType()),
                gatewayAccount.isLive());
        final StripeCreateChargeResponse createChargeResponse = jsonObjectMapper.getObject(jsonResponse, StripeCreateChargeResponse.class);
        return new StripeAuthorisationResponse(createChargeResponse);
    }

    private StripeAuthorisationResponse createCharge(CardAuthorisationGatewayRequest request, String sourceId)
            throws GatewayClientException, GatewayException, DownstreamException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        String jsonResponse = postToStripe(
                "/v1/charges",
                authorisePayload(request, sourceId),
                format("gateway-operations.%s.%s.authorise.create_charge",
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType()),
                gatewayAccount.isLive());
        final StripeCreateChargeResponse createChargeResponse = jsonObjectMapper.getObject(jsonResponse, StripeCreateChargeResponse.class);
        return new StripeAuthorisationResponse(createChargeResponse);
    }

    private StripeSourcesResponse createSource(CardAuthorisationGatewayRequest request, String tokenId)
            throws GatewayClientException, GatewayException, DownstreamException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        String jsonResponse = postToStripe(
                "/v1/sources",
                sourcesPayload(tokenId),
                format("gateway-operations.%s.%s.authorise.create_source",
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType()),
                gatewayAccount.isLive());
        return jsonObjectMapper.getObject(jsonResponse, StripeSourcesResponse.class);
    }

    private StripeTokenResponse createToken(CardAuthorisationGatewayRequest request)
            throws GatewayClientException, GatewayException, DownstreamException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        String jsonResponse = postToStripe(
                "/v1/tokens",
                tokenPayload(request),
                format("gateway-operations.%s.%s.authorise.create_token",
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType()),
                gatewayAccount.isLive());
        return jsonObjectMapper.getObject(jsonResponse, StripeTokenResponse.class);
    }

    private String postToStripe(String path, String payload, String metricsPrefix, boolean isLiveAccount)
            throws GatewayClientException, GatewayException, DownstreamException {
        return client.postRequest(
                URI.create(stripeGatewayConfig.getUrl() + path),
                payload,
                ImmutableMap.of(AUTHORIZATION, StripeAuthUtil.getAuthHeaderValue(stripeGatewayConfig, isLiveAccount)),
                APPLICATION_FORM_URLENCODED_TYPE,
                metricsPrefix
        );
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseWallet(WalletAuthorisationGatewayRequest request) {
        throw new UnsupportedOperationException("Wallets are not supported for Stripe");
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return stripeCaptureHandler.capture(request);
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        return stripeRefundHandler.refund(request);
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) {
        return stripeCancelHandler.cancel(request);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
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

    private String authorisePayload(CardAuthorisationGatewayRequest request, String sourceId) {
        return buildAuthorisePayload(sourceId, request.getChargeExternalId(), request.getAmount(), request.getDescription(), request.getGatewayAccount());
    }

    private String authorise3DSChargePayload(Auth3dsResponseGatewayRequest request, String sourceId) {
        return buildAuthorisePayload(sourceId, request.getChargeExternalId(), request.getAmount(), request.getDescription(), request.getGatewayAccount());
    }

    private String buildAuthorisePayload(String sourceId, String externalId, String amount, String description, GatewayAccountEntity gatewayAccount) {
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("amount", amount));
        params.add(new BasicNameValuePair("currency", "GBP"));
        params.add(new BasicNameValuePair("description", description));
        params.add(new BasicNameValuePair("source", sourceId));
        params.add(new BasicNameValuePair("capture", "false"));
        String stripeAccountId = getStripeAccountId(externalId, gatewayAccount);

        params.add(new BasicNameValuePair("destination[account]", stripeAccountId));
        return URLEncodedUtils.format(params, UTF_8);
    }

    private String getStripeAccountId(String externalId, GatewayAccountEntity gatewayAccount) {
        String stripeAccountId = gatewayAccount.getCredentials().get("stripe_account_id");

        if (StringUtils.isBlank(stripeAccountId)) {
            logger.error("Unable to process charge [{}]. There is no stripe_account_id for corresponding gateway account with id {}",
                    externalId, gatewayAccount.getId());
            throw new WebApplicationException(format("There is no stripe_account_id for gateway account with id %s", gatewayAccount.getId()));
        }
        return stripeAccountId;
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
}
