package uk.gov.pay.connector.service.smartpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayRequest;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.Notifications.Builder;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BasePaymentProvider;
import uk.gov.pay.connector.service.BaseResponse;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.GatewayOperation;
import uk.gov.pay.connector.service.GatewayOrder;
import uk.gov.pay.connector.service.PaymentGatewayName;
import uk.gov.pay.connector.service.StatusMapper;

import javax.ws.rs.client.Invocation;
import java.util.EnumMap;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderRequestBuilder.aSmartpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderRequestBuilder.aSmartpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderRequestBuilder.aSmartpayCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderRequestBuilder.aSmartpayRefundOrderRequestBuilder;

public class SmartpayPaymentProvider extends BasePaymentProvider<BaseResponse, Pair<String, Boolean>> {

    private final ObjectMapper objectMapper;

    public SmartpayPaymentProvider(EnumMap<GatewayOperation, GatewayClient> clients, ObjectMapper objectMapper) {
        super(clients);
        this.objectMapper = objectMapper;
    }

    @Override
    public String getPaymentGatewayName() {
        return PaymentGatewayName.SMARTPAY.getName();
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    @Override
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        return sendReceive(request, buildAuthoriseOrderFor(), SmartpayAuthorisationResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse<BaseResponse> authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        return GatewayResponse.with(new GatewayError("3D Secure not implemented for SmartPay", GENERIC_GATEWAY_ERROR));
    }

    @Override
    public GatewayResponse capture(CaptureGatewayRequest request) {
        return sendReceive(request, buildCaptureOrderFor(), SmartpayCaptureResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse refund(RefundGatewayRequest request) {
        return sendReceive(request, buildRefundOrderFor(), SmartpayRefundResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse cancel(CancelGatewayRequest request) {
        return sendReceive(request, buildCancelOrderFor(), SmartpayCancelResponse.class, extractResponseIdentifier());

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

    public static BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> includeSessionIdentifier() {
        return (order, builder) -> builder;
    }

    private Function<AuthorisationGatewayRequest, GatewayOrder> buildAuthoriseOrderFor() {
        return request -> aSmartpayAuthoriseOrderRequestBuilder()
                .withMerchantCode(getMerchantCode(request))
                .withPaymentPlatformReference(request.getChargeExternalId())
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails())
                .build();
    }

    private Function<CaptureGatewayRequest, GatewayOrder> buildCaptureOrderFor() {
        return request -> aSmartpayCaptureOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(getMerchantCode(request))
                .withAmount(request.getAmount())
                .build();
    }

    private Function<CancelGatewayRequest, GatewayOrder> buildCancelOrderFor() {
        return request -> aSmartpayCancelOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(getMerchantCode(request))
                .build();
    }

    private Function<RefundGatewayRequest, GatewayOrder> buildRefundOrderFor() {
        return request -> aSmartpayRefundOrderRequestBuilder()
                .withReference(request.getReference())
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(getMerchantCode(request))
                .withAmount(request.getAmount())
                .build();
    }

    private String getMerchantCode(GatewayRequest request) {
        return request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID);
    }

    private Function<GatewayClient.Response, Optional<String>> extractResponseIdentifier() {
        return response -> {
            Optional<String> emptyResponseIdentifierForSmartpay = Optional.empty();
            return emptyResponseIdentifierForSmartpay;
        };
    }
}
