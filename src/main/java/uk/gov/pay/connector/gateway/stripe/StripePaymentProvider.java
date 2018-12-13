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
import uk.gov.pay.connector.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCancelHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCaptureHandler;
import uk.gov.pay.connector.gateway.stripe.handler.StripeRefundHandler;
import uk.gov.pay.connector.gateway.stripe.json.StripeCreateChargeResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeSourcesResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeTokenResponse;
import uk.gov.pay.connector.gateway.stripe.util.StripeAuthUtil;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;
import static uk.gov.pay.connector.gateway.model.GatewayError.unexpectedStatusCodeFromGateway;

@Singleton
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentProvider.class);

    private final StripeGatewayClient client;
    private final String frontendUrl;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final StripeCaptureHandler stripeCaptureHandler;
    private final StripeCancelHandler stripeCancelHandler;
    private final StripeRefundHandler stripeRefundHandler;

    @Inject
    public StripePaymentProvider(StripeGatewayClient stripeGatewayClient,
                                 ConnectorConfiguration configuration) {
        this.stripeGatewayConfig = configuration.getStripeConfig();
        this.client = stripeGatewayClient;
        this.frontendUrl = configuration.getLinks().getFrontendUrl();
        this.externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        stripeCaptureHandler = new StripeCaptureHandler(client, stripeGatewayConfig);
        stripeCancelHandler = new StripeCancelHandler(client, stripeGatewayConfig);
        stripeRefundHandler = new StripeRefundHandler(client, stripeGatewayConfig);
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
    public GatewayResponse authorise(CardAuthorisationGatewayRequest request) {
        logger.info("Calling Stripe for authorisation of charge [{}]", request.getChargeExternalId());

        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();

        try {
            Response tokenResponse = createToken(request);
            Response sourceResponse = createSource(
                    request,
                    tokenResponse.readEntity(StripeTokenResponse.class).getId()
            );

            StripeSourcesResponse stripeSourcesResponse = sourceResponse.readEntity(StripeSourcesResponse.class);

            if (stripeSourcesResponse.require3ds()) {
                Response source3dsResponse = create3dsSource(request, stripeSourcesResponse.getId());

                return responseBuilder.withResponse(Stripe3dsSourceAuthorisationResponse.of(source3dsResponse)).build();
            } else {
                Response authorisationResponse = createCharge(request, stripeSourcesResponse.getId());
                StripeAuthorisationResponse stripeAuthResponse = new StripeAuthorisationResponse(authorisationResponse.readEntity(StripeCreateChargeResponse.class));
                return responseBuilder.withResponse(stripeAuthResponse).build();
            }
        } catch (GatewayClientException e) {

            Response response = e.getResponse();

            if (response.getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                return unexpectedGatewayResponse(request.getChargeExternalId(), responseBuilder, e.getMessage(), HttpStatus.SC_UNAUTHORIZED);
            }

            StripeErrorResponse stripeErrorResponse = response.readEntity(StripeErrorResponse.class);
            logger.error("Authorisation failed for charge {}. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
                    request.getChargeExternalId(), stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), response.getStatus());
            GatewayError gatewayError = genericGatewayError(stripeErrorResponse.getError().getMessage());

            return responseBuilder.withGatewayError(gatewayError).build();

        } catch (DownstreamException e) {
            return unexpectedGatewayResponse(request.getChargeExternalId(), responseBuilder, e.getMessage(), e.getStatusCode());
        } catch (GatewayException e) {
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
            Response authorisationResponse = createChargeFor3DSSource(request, request.getTransactionId().get());
            StripeAuthorisationResponse stripeAuthResponse = new StripeAuthorisationResponse(authorisationResponse.readEntity(StripeCreateChargeResponse.class));

            return Gateway3DSAuthorisationResponse.of(stripeAuthResponse.authoriseStatus(), stripeAuthResponse.getTransactionId());
        } catch (GatewayClientException e) {

            Response response = e.getResponse();

            if (response.getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.ERROR);
            }

            StripeErrorResponse stripeErrorResponse = response.readEntity(StripeErrorResponse.class);
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
        String errorId = UUID.randomUUID().toString();
        logger.error("Authorisation failed for charge {}. Reason: {}. Status code from Stripe: {}. ErrorId: {}",
                chargeExternalId, errorMessage, statusCode, errorId);
        GatewayError gatewayError = unexpectedStatusCodeFromGateway("There was an internal server error. ErrorId: " + errorId);
        return responseBuilder.withGatewayError(gatewayError).build();
    }

    private Response create3dsSource(CardAuthorisationGatewayRequest request, String sourceId) throws GatewayClientException, GatewayException, DownstreamException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        return postToStripe(
                "/v1/sources",
                threeDSecurePayload(request, sourceId),
                format("gateway-operations.%s.%s.authorise.create_3ds_source",
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType())
        );
    }

    private Response createChargeFor3DSSource(Auth3dsResponseGatewayRequest request, String sourceId)
            throws GatewayClientException, GatewayException, DownstreamException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        return postToStripe(
                "/v1/charges",
                authorise3DSChargePayload(request, sourceId),
                format("gateway-operations.%s.%s.authorise.create_charge",
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType())
        );
    }

    private Response createCharge(CardAuthorisationGatewayRequest request, String sourceId) throws GatewayClientException, GatewayException, DownstreamException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        return postToStripe(
                "/v1/charges",
                authorisePayload(request, sourceId),
                format("gateway-operations.%s.%s.authorise.create_charge",
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType())
        );
    }

    private Response createSource(CardAuthorisationGatewayRequest request, String tokenId) throws GatewayClientException, GatewayException, DownstreamException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        return postToStripe(
                "/v1/sources",
                sourcesPayload(tokenId),
                format("gateway-operations.%s.%s.authorise.create_source",
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType())
        );
    }

    private Response createToken(CardAuthorisationGatewayRequest request) throws GatewayClientException, GatewayException, DownstreamException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        return postToStripe(
                "/v1/tokens",
                tokenPayload(request),
                format("gateway-operations.%s.%s.authorise.create_token",
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType())
        );
    }

    private Response postToStripe(String path, String payload, String metricsPrefix) throws GatewayClientException, GatewayException, DownstreamException {
        return client.postRequest(
                URI.create(stripeGatewayConfig.getUrl() + path),
                payload,
                ImmutableMap.of(AUTHORIZATION, StripeAuthUtil.getAuthHeaderValue(stripeGatewayConfig)),
                APPLICATION_FORM_URLENCODED_TYPE,
                metricsPrefix
        );
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseApplePay(ApplePayAuthorisationGatewayRequest request) {
        throw new UnsupportedOperationException("Apple Pay is not supported for Stripe");
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return stripeCaptureHandler.capture(request);
    }

    @Override
    public GatewayResponse<BaseRefundResponse> refund(RefundGatewayRequest request) {
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
        return URLEncodedUtils.format(params, UTF_8);
    }
}
