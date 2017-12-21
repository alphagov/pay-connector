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
import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BaseAuthoriseResponse;
import uk.gov.pay.connector.service.BaseCaptureResponse;
import uk.gov.pay.connector.service.BaseResponse;
import uk.gov.pay.connector.service.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.GatewayOperation;
import uk.gov.pay.connector.service.GatewayOrder;
import uk.gov.pay.connector.service.InterpretedStatus;
import uk.gov.pay.connector.service.PaymentGatewayName;
import uk.gov.pay.connector.service.PaymentProviderNotificationHandler;
import uk.gov.pay.connector.service.PaymentProviderOperations;
import uk.gov.pay.connector.service.StatusMapper;
import uk.gov.pay.connector.service.epdq.EpdqStatusMapper;

import javax.ws.rs.client.Invocation;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static fj.data.Either.left;
import static fj.data.Either.reduce;
import static fj.data.Either.right;
import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderRequestBuilder.aSmartpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderRequestBuilder.aSmartpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderRequestBuilder.aSmartpayCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderRequestBuilder.aSmartpayRefundOrderRequestBuilder;

public class SmartpayPaymentProvider implements PaymentProviderOperations {

    private final ObjectMapper objectMapper;
    protected boolean isNotificationEndpointSecured;
    protected String notificationDomain;
    protected ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    protected EnumMap<GatewayOperation, GatewayClient> gatewayOperationClientMap;

    public SmartpayPaymentProvider(EnumMap<GatewayOperation, GatewayClient> clients, ObjectMapper objectMapper,
                                   ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator) {
        this.gatewayOperationClientMap = clients;
        this.isNotificationEndpointSecured = false;
        this.notificationDomain = null;
        this.externalRefundAvailabilityCalculator = externalRefundAvailabilityCalculator;
        this.objectMapper = objectMapper;
    }

    public PaymentGatewayName getPaymentGatewayName() {
        return PaymentGatewayName.SMARTPAY;
    }

    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    public GatewayResponse<SmartpayAuthorisationResponse> authorise(AuthorisationGatewayRequest request) {
        return sendReceive(request, buildAuthoriseOrderFor(), SmartpayAuthorisationResponse.class, extractResponseIdentifier());
    }

    public GatewayResponse<BaseAuthoriseResponse> authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        return GatewayResponse.with(new GatewayError("3D Secure not implemented for SmartPay", GENERIC_GATEWAY_ERROR));
    }

    public GatewayResponse<SmartpayCaptureResponse> capture(CaptureGatewayRequest request) {
        return sendReceive(request, buildCaptureOrderFor(), SmartpayCaptureResponse.class, extractResponseIdentifier());
    }

    public GatewayResponse refund(RefundGatewayRequest request) {
        return sendReceive(request, buildRefundOrderFor(), SmartpayRefundResponse.class, extractResponseIdentifier());
    }

    public GatewayResponse cancel(CancelGatewayRequest request) {
        return sendReceive(request, buildCancelOrderFor(), SmartpayCancelResponse.class, extractResponseIdentifier());

    }

    public String getNotificationDomain() {
        return null;
    }

    public static class SmartpayParseError extends Exception {
        public SmartpayParseError(Throwable cause) {
            super(cause);
        }
    }

    public List<SmartpayNotification> parseNotification(String payload) throws SmartpayParseError {
        try {
            return objectMapper.readValue(payload, SmartpayNotificationList.class).getNotifications();
//                    // TODO for authorisation notifications, this does the wrong thing
//                    // Transaction ID is pspReference, not originalReference as the code below assumes
//                    // https://www.barclaycard.co.uk/business/files/SmartPay_Notifications_Guide.pdf
//                    // We will set the transaction ID to blank, which makes the notification effectively useless
//                    // This is OK at the moment because we ignore authorisation notifications for Smartpay
//                    .forEach(notification -> builder.addNotificationFor(
//                            notification.getOriginalReference(),
//                            notification.getPspReference(),
//                            notification.getStatus(),
//                            notification.getEventDate(),
//                            null
//                    ));

        } catch (Exception e) {
            throw new SmartpayParseError(e);
        }
    }

    public InterpretedStatus from(Pair<String, Boolean> gatewayStatus, ChargeStatus currentStatus) {
        return SmartpayStatusMapper.from(gatewayStatus, currentStatus);
    }

    public InterpretedStatus from(Pair<String, Boolean> gatewayStatus) {
        return SmartpayStatusMapper.from(gatewayStatus);
    }

    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
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

    protected <U extends GatewayRequest> GatewayResponse sendReceive(U request, Function<U, GatewayOrder> order,
                                                                     Class<? extends BaseResponse> clazz,
                                                                     Function<GatewayClient.Response, Optional<String>> responseIdentifier) {

        return sendReceive(null, request, order, clazz, responseIdentifier);
    }

    protected <U extends GatewayRequest> GatewayResponse sendReceive(String route, U request, Function<U, GatewayOrder> order,
                                                                     Class<? extends BaseResponse> clazz,
                                                                     Function<GatewayClient.Response, Optional<String>> responseIdentifier) {
        GatewayClient gatewayClient = gatewayOperationClientMap.get(request.getRequestType());
        return reduce(
                gatewayClient
                        .postRequestFor(route, request.getGatewayAccount(), order.apply(request))
                        .bimap(
                                GatewayResponse::with,
                                r -> mapToResponse(r, clazz, responseIdentifier, gatewayClient)
                        )
        );
    }

    private GatewayResponse mapToResponse(GatewayClient.Response response,
                                          Class<? extends BaseResponse> clazz,
                                          Function<GatewayClient.Response, Optional<String>> responseIdentifier,
                                          GatewayClient client) {
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();

        reduce(
                client.unmarshallResponse(response, clazz)
                        .bimap(
                                responseBuilder::withGatewayError,
                                responseBuilder::withResponse
                        )
        );

        responseIdentifier.apply(response)
                .ifPresent(responseBuilder::withSessionIdentifier);

        return responseBuilder.build();

    }
}
