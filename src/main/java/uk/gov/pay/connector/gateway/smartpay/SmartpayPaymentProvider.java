package uk.gov.pay.connector.gateway.smartpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import fj.data.Either;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.StatusMapper;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.GatewayResponseGenerator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.Notification;
import uk.gov.pay.connector.usernotification.model.Notifications;
import uk.gov.pay.connector.usernotification.model.Notifications.Builder;

import javax.inject.Inject;
import javax.ws.rs.client.Invocation;
import java.util.Optional;
import java.util.function.BiFunction;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class SmartpayPaymentProvider implements PaymentProvider<BaseResponse, Pair<String, Boolean>> {

    private final ObjectMapper objectMapper;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final GatewayClient client;
    
    private final SmartpayCaptureHandler smartpayCaptureHandler;

    @Inject
    public SmartpayPaymentProvider(ConnectorConfiguration configuration,
                                   GatewayClientFactory gatewayClientFactory,
                                   Environment environment,
                                   ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        client = gatewayClientFactory.createGatewayClient(SMARTPAY, configuration.getGatewayConfigFor(SMARTPAY).getUrls(), includeSessionIdentifier(), environment.metrics());
        
        this.smartpayCaptureHandler = new SmartpayCaptureHandler(client);
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return SMARTPAY;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    @Override
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = client.postRequestFor(null, request.getGatewayAccount(), buildAuthoriseOrderFor(request));
        return GatewayResponseGenerator.getSmartpayGatewayResponse(client, response, SmartpayAuthorisationResponse.class);
    }

    @Override
    public GatewayResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = client.postRequestFor(null, request.getGatewayAccount(), build3dsResponseAuthOrderFor(request));
        return GatewayResponseGenerator.getSmartpayGatewayResponse(client, response, SmartpayAuthorisationResponse.class);
    }

    @Override
    public CaptureHandler getCaptureHandler() {
        return smartpayCaptureHandler;
    }

    @Override
    public GatewayResponse refund(RefundGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = client.postRequestFor(null, request.getGatewayAccount(), buildRefundOrderFor(request));
        return GatewayResponseGenerator.getSmartpayGatewayResponse(client, response, SmartpayRefundResponse.class);
    }

    @Override
    public GatewayResponse cancel(CancelGatewayRequest request) { 
        Either<GatewayError, GatewayClient.Response> response = client.postRequestFor(null, request.getGatewayAccount(), buildCancelOrderFor(request));
        return GatewayResponseGenerator.getSmartpayGatewayResponse(client, response, SmartpayCancelResponse.class);

    }

    @Override
    public Boolean isNotificationEndpointSecured() {
        return false;
    }

    @Override
    public String getNotificationDomain() {
        return null;
    }

    @Override
    public boolean verifyNotification(Notification<Pair<String, Boolean>> notification, GatewayAccountEntity gatewayAccountEntity) {
        return true;
    }

    @Override
    public Either<String, Notifications<Pair<String, Boolean>>> parseNotification(String payload) {
        try {
            Builder<Pair<String, Boolean>> builder = Notifications.builder();

            objectMapper.readValue(payload, SmartpayNotificationList.class)
                    .getNotifications()
                    // TODO for authorisation notifications, this does the wrong thing
                    // Transaction ID is pspReference, not originalReference as the code below assumes
                    // https://www.barclaycard.co.uk/business/files/SmartPay_Notifications_Guide.pdf
                    // We will set the transaction ID to blank, which makes the notification effectively useless
                    // This is OK at the moment because we ignore authorisation notifications for Smartpay
                    .forEach(notification -> builder.addNotificationFor(
                            notification.getOriginalReference(),
                            notification.getPspReference(),
                            notification.getStatus(),
                            notification.getEventDate(),
                            null
                    ));

            return right(builder.build());
        } catch (Exception e) {
            return left(e.getMessage());
        }
    }

    @Override
    public StatusMapper<Pair<String, Boolean>> getStatusMapper() {
        return SmartpayStatusMapper.get();
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }

    private GatewayOrder buildAuthoriseOrderFor(AuthorisationGatewayRequest request) {
        SmartpayOrderRequestBuilder smartpayOrderRequestBuilder = request.getGatewayAccount().isRequires3ds() ?
                SmartpayOrderRequestBuilder.aSmartpay3dsRequiredOrderRequestBuilder() : SmartpayOrderRequestBuilder.aSmartpayAuthoriseOrderRequestBuilder();

        return smartpayOrderRequestBuilder
                .withMerchantCode(getMerchantCode(request))
                .withPaymentPlatformReference(request.getChargeExternalId())
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails())
                .build();
    }

    private GatewayOrder build3dsResponseAuthOrderFor(Auth3dsResponseGatewayRequest request) {
        return SmartpayOrderRequestBuilder.aSmartpayAuthorise3dsOrderRequestBuilder()
                .withPaResponse(request.getAuth3DsDetails().getPaResponse())
                .withMd(request.getAuth3DsDetails().getMd())
                .withMerchantCode(getMerchantCode(request))
                .build();
    }

    private GatewayOrder buildCancelOrderFor(CancelGatewayRequest request) {
        return SmartpayOrderRequestBuilder.aSmartpayCancelOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(getMerchantCode(request))
                .build();
    }

    private GatewayOrder buildRefundOrderFor(RefundGatewayRequest request) {
        return SmartpayOrderRequestBuilder.aSmartpayRefundOrderRequestBuilder()
                .withReference(request.getReference())
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(getMerchantCode(request))
                .withAmount(request.getAmount())
                .build();
    }

    private String getMerchantCode(GatewayRequest request) {
        return request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID);
    }
    
    public static BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> includeSessionIdentifier() {
        return (order, builder) -> builder;
    }
}
