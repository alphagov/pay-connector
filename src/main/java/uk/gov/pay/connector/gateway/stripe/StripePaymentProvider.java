package uk.gov.pay.connector.gateway.stripe;

import com.google.common.collect.ImmutableMap;
import fj.data.Either;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.StatusMapper;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCaptureHandler;
import uk.gov.pay.connector.gateway.stripe.json.StripeCreateChargeResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeSourcesResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeTokenResponse;
import uk.gov.pay.connector.gateway.stripe.util.StripeAuthUtil;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.Notification;
import uk.gov.pay.connector.usernotification.model.Notifications;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;
import static uk.gov.pay.connector.gateway.model.GatewayError.unexpectedStatusCodeFromGateway;

@Singleton
public class StripePaymentProvider implements PaymentProvider<String> {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentProvider.class);

    private final StripeGatewayClient client;
    private final String frontendUrl;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final StripeCaptureHandler stripeCaptureHandler;

    @Inject
    public StripePaymentProvider(StripeGatewayClient stripeGatewayClient,
                                 ConnectorConfiguration configuration) {
        this.stripeGatewayConfig = configuration.getStripeConfig();
        this.client = stripeGatewayClient;
        this.frontendUrl = configuration.getLinks().getFrontendUrl();
        this.externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        stripeCaptureHandler = new StripeCaptureHandler(client, stripeGatewayConfig);
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return STRIPE;
    }

    @Override
    public StatusMapper getStatusMapper() {
        return null;
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
            int status = response.getStatus();

            if (status == HttpStatus.SC_UNAUTHORIZED) {
                return unexpectedGatewayResponse(request.getChargeExternalId(), responseBuilder, e.getMessage(), HttpStatus.SC_UNAUTHORIZED);
            }

            StripeErrorResponse stripeErrorResponse = response.readEntity(StripeErrorResponse.class);
            String errorId = UUID.randomUUID().toString();
            logger.error("Authorisation failed for charge {}. Failure code from Stripe: {}, failure message from Stripe: {}. ErrorId: {}. Response code from Stripe: {}",
                    request.getChargeExternalId(), stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), errorId, response.getStatus());
            GatewayError gatewayError = genericGatewayError(stripeErrorResponse.getError().getMessage());

            return responseBuilder.withGatewayError(gatewayError).build();

        } catch (DownstreamException e) {

            return unexpectedGatewayResponse(request.getChargeExternalId(), responseBuilder, e.getMessage(), e.getStatusCode());

        } catch (GatewayException e) {
            return responseBuilder.withGatewayError(GatewayError.of(e)).build();
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

    private Response createCharge(CardAuthorisationGatewayRequest request, String sourceId) throws GatewayClientException, GatewayException, DownstreamException {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        return postToStripe(
                "/v1/charges",
                authorisePayload(request, sourceId),
                format("gateway-operations.%s.%s.authorise.create_source",
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
    public GatewayResponse<BaseAuthoriseResponse> authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        return null;
    }

    @Override
    public GatewayResponse<BaseCaptureResponse> capture(CaptureGatewayRequest request) {
        return stripeCaptureHandler.capture(request);
    }

    @Override
    public GatewayResponse<BaseRefundResponse> refund(RefundGatewayRequest request) {
        return null;
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) {
        return null;
    }

    @Override
    public Either<String, Notifications<String>> parseNotification(String payload) {
        return null;
    }

    @Override
    public Boolean isNotificationEndpointSecured() {
        return null;
    }

    @Override
    public String getNotificationDomain() {
        return null;
    }

    @Override
    public boolean verifyNotification(Notification<String> notification, GatewayAccountEntity gatewayAccountEntity) {
        return false;
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }

    private String sourcesPayload(String token) {
        Map<String, String> params = new HashMap<>();
        params.put("type", "card");
        params.put("token", token);
        params.put("usage", "single_use");
        return encode(params);
    }

    private String threeDSecurePayload(CardAuthorisationGatewayRequest request, String sourceId) {
        //todo: revisit for frontendUrl format
        String frontend3dsIncomingUrl = String.format("%s/card_details/%s/3ds_required_in/stripe", frontendUrl, request.getChargeExternalId());
        Map<String, String> params = new HashMap<>();
        params.put("type", "three_d_secure");
        params.put("amount", request.getAmount());
        params.put("currency", "GBP");
        params.put("redirect[return_url]", frontend3dsIncomingUrl);
        params.put("three_d_secure[card]", sourceId);
        return encode(params);
    }

    private String authorisePayload(CardAuthorisationGatewayRequest request, String sourceId) {
        Map<String, String> params = new HashMap<>();
        params.put("amount", request.getAmount());
        params.put("currency", "GBP");
        params.put("description", request.getDescription());
        params.put("source", sourceId);
        params.put("capture", "false");
        String stripeAccountId = request.getGatewayAccount().getCredentials().get("stripe_account_id");

        if (StringUtils.isBlank(stripeAccountId))
            throw new WebApplicationException(format("There is no stripe_account_id for gateway account with id %s", request.getGatewayAccount().getId()));

        params.put("destination[account]", stripeAccountId);
        return encode(params);
    }

    private String tokenPayload(CardAuthorisationGatewayRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("card[cvc]", request.getAuthCardDetails().getCvc());
        params.put("card[exp_month]", request.getAuthCardDetails().expiryMonth());
        params.put("card[exp_year]", request.getAuthCardDetails().expiryYear());
        params.put("card[number]", request.getAuthCardDetails().getCardNo());
        return encode(params);
    }

    private String encode(Map<String, String> params) {
        return params.keySet().stream()
                .map(key -> encode(key) + "=" + encode(params.get(key)))
                .collect(joining("&"));
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(format("Exception thrown when encoding %s", value));
        }
    }
}
