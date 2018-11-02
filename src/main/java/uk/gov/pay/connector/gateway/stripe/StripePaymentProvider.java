package uk.gov.pay.connector.gateway.stripe;

import fj.data.Either;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.StatusMapper;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.Notification;
import uk.gov.pay.connector.usernotification.model.Notifications;

import javax.inject.Inject;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;

public class StripePaymentProvider implements PaymentProvider<BaseResponse, String> {

    private static final Logger logger = LoggerFactory.getLogger(StripePaymentProvider.class);

    private GatewayClient authoriseClient;

    @Inject
    public StripePaymentProvider(ConnectorConfiguration configuration,
                                 GatewayClientFactory gatewayClientFactory,
                                 Environment environment) {
        this.authoriseClient = gatewayClientFactory.createGatewayClient(
                PaymentGatewayName.STRIPE,
                GatewayOperation.AUTHORISE,
                configuration.getGatewayConfigFor(STRIPE).getUrls(),
                includeSessionIdentifier(),
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

        Response authorisationResponse;
        Response sourceResponse;
        try {
            sourceResponse = authoriseClient.postStripeRequestFor(
                    request.getGatewayAccount(), 
                    StripeGatewayOrder.newSource(request), 
                    "/v1/sources");
            String sourceId = sourceResponse.readEntity(Map.class).get("id").toString();
            authorisationResponse = authoriseClient.postStripeRequestFor(
                    request.getGatewayAccount(), 
                    StripeGatewayOrder.anAuthorisationOrder(request, sourceId), 
                    "/v1/charges");
        } catch (GatewayError gatewayError) {
            return GatewayResponse.with(gatewayError);
        } 

        logger.error("returning GatewayResponse from StripePaymentProvider");
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
        return responseBuilder.withResponse(StripeAuthorisationResponse.of(authorisationResponse)).build();
    }

    @Override
    public GatewayResponse<BaseResponse> authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        return null;
    }

    @Override
    public GatewayResponse<BaseResponse> capture(CaptureGatewayRequest request) {
        return null;
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
        return null;
    }

    static BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> includeSessionIdentifier() {
        return (order, builder) -> builder;
    }
}
