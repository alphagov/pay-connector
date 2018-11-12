package uk.gov.pay.connector.gateway.stripe;

import com.google.common.collect.ImmutableMap;
import fj.data.Either;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.StatusMapper;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeError;
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
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE;

@Singleton
public class StripePaymentProvider implements PaymentProvider<BaseResponse, String> {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentProvider.class);

    private final StripeGatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;

    @Inject
    public StripePaymentProvider(GatewayClientFactory gatewayClientFactory,
                                 Environment environment,
                                 ConnectorConfiguration configuration) {
        this.stripeGatewayConfig = configuration.getStripeConfig();
        this.client = gatewayClientFactory.createStripeGatewayClient(STRIPE, GatewayOperation.AUTHORISE, environment.metrics());
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
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        logger.info("Calling Stripe for authorisation of charge [{}]", request.getChargeExternalId());

        Response sourceResponse = client.postRequest(
                request.getGatewayAccount(),
                AUTHORISE,
                URI.create(stripeGatewayConfig.getUrl() + "/v1/tokens"),
                sourcePayload(request),
                getAuthHeaderValue(),
                APPLICATION_FORM_URLENCODED_TYPE);

        if (sourceResponse.getStatusInfo().getFamily() == Response.Status.Family.CLIENT_ERROR) {
            String reason = sourceResponse.readEntity(StripeError.class).getError().getMessage();
            String errorId = UUID.randomUUID().toString();
            logger.error("There was error calling /v1/tokens. Reason: {}, ErrorId: {}", reason, errorId);
            throw new WebApplicationException("There was an internal server error. ErrorId: " + errorId);
        }
        
        String token = sourceResponse.readEntity(Map.class).get("id").toString();
        Response authorisationResponse = client.postRequest(
                request.getGatewayAccount(),
                AUTHORISE,
                URI.create(stripeGatewayConfig.getUrl() + "/v1/charges"),
                authorisePayload(request, token),
                getAuthHeaderValue(),
                APPLICATION_FORM_URLENCODED_TYPE);

        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
        return responseBuilder.withResponse(StripeAuthorisationResponse.of(authorisationResponse)).build();
    }

    private String getAuthHeaderValue() {
        return "Bearer " + stripeGatewayConfig.getAuthToken();
    }

    @Override
    public GatewayResponse<BaseResponse> authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        return null;
    }

    @Override
    public CaptureHandler getCaptureHandler() {
        throw new UnsupportedOperationException();
    }

    @Override
    public GatewayResponse<BaseResponse> refund(RefundGatewayRequest request) {
        return null;
    }

    @Override
    public GatewayResponse<BaseResponse> cancel(CancelGatewayRequest request) {
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
        throw new UnsupportedOperationException();
    }

    private String authorisePayload(AuthorisationGatewayRequest request, String token) {
        Map<String, String> params = new HashMap<>();
        params.put("amount", request.getAmount());
        params.put("currency", "GBP");
        params.put("description", request.getDescription());
        params.put("source", token);
        params.put("capture", "false");
        String stripeAccountId = request.getGatewayAccount().getCredentials().get("stripe_account_id");

        if (StringUtils.isBlank(stripeAccountId)) {
            throw new WebApplicationException(format("There is no stripe_account_id for gateway account with id %s", request.getGatewayAccount().getId()));
        }

        params.put("destination[account]", stripeAccountId);
        
        return params.keySet().stream()
                .map(key -> encode(key) + "=" + encode(params.get(key)))
                .collect(joining("&"));
    }

    private String sourcePayload(AuthorisationGatewayRequest request) {
        Map<String, String> sourceParams = ImmutableMap.of(
                "card[cvc]", request.getAuthCardDetails().getCvc(),
                "card[exp_month]", request.getAuthCardDetails().expiryMonth(),
                "card[exp_year]", request.getAuthCardDetails().expiryYear(),
                "card[number]", request.getAuthCardDetails().getCardNo());
        
        return sourceParams.keySet().stream()
                .map(key -> encode(key) + "=" + encode(sourceParams.get(key)))
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
