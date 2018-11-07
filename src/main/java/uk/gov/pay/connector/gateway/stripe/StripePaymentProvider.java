package uk.gov.pay.connector.gateway.stripe;

import fj.data.Either;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
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
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.Notification;
import uk.gov.pay.connector.usernotification.model.Notifications;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE;

public class StripePaymentProvider implements PaymentProvider<BaseResponse, String> {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentProvider.class);

    private final StripeGatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;

    @Inject
    public StripePaymentProvider(GatewayClientFactory gatewayClientFactory,
                                 Environment environment,
                                 ConnectorConfiguration configuration) {
        this.stripeGatewayConfig = configuration.getStripeConfig();
        this.client = gatewayClientFactory.createStripeGatewayClient(
                PaymentGatewayName.STRIPE,
                GatewayOperation.AUTHORISE,
                environment.metrics()
        );
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
                URI.create(stripeGatewayConfig.getUrl() + "/v1/sources"),
                stripeSourcePayload(request),
                getAuthHeaderValue());
        String sourceId = sourceResponse.readEntity(Map.class).get("id").toString();
        Response authorisationResponse = client.postRequest(
                request.getGatewayAccount(),
                AUTHORISE,
                URI.create(stripeGatewayConfig.getUrl() + "/v1/charges"),
                stripeAuthorisePayload(request, sourceId),
                getAuthHeaderValue());

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

    private String stripeAuthorisePayload(AuthorisationGatewayRequest request, String sourceId) {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", request.getAmount());
        params.put("currency", "GBP");
        params.put("description", request.getDescription());
        params.put("source", sourceId);
        params.put("capture", false);
        Map<String, Object> destinationParams = new HashMap<>();
        String stripeAccountId = request.getGatewayAccount().getCredentials().get("stripe_account_id");

        if (StringUtils.isBlank(stripeAccountId)) {
            throw new WebApplicationException(format("There is no stripe_account_id for gateway account with id %s", request.getGatewayAccount().getId()));
        }

        destinationParams.put("account", stripeAccountId);
        params.put("destination", destinationParams);
        return new JSONObject(params).toString();
    }

    private String stripeSourcePayload(AuthorisationGatewayRequest request) {
        Map<String, Object> sourceParams = new HashMap<>();
        sourceParams.put("type", "card");
        sourceParams.put("amount", request.getAmount());
        sourceParams.put("currency", "GBP");
        sourceParams.put("usage", "single_use");
        Map<String, Object> ownerParams = new HashMap<>();
        ownerParams.put("name", request.getAuthCardDetails().getCardHolder());
        sourceParams.put("owner", ownerParams);
        return new JSONObject(sourceParams).toString();
    }
}
