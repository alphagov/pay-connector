package uk.gov.pay.connector.service.epdq;

import fj.data.Either;
import org.apache.http.NameValuePair;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BasePaymentProvider;
import uk.gov.pay.connector.service.BaseResponse;
import uk.gov.pay.connector.service.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.GatewayOperation;
import uk.gov.pay.connector.service.GatewayOrder;
import uk.gov.pay.connector.service.PaymentGatewayName;
import uk.gov.pay.connector.service.StatusMapper;

import javax.ws.rs.client.Invocation;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.util.stream.Collectors.toList;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.service.epdq.EpdqNotification.SHASIGN;
import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.anEpdq3DsAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.anEpdqAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.anEpdqCancelOrderRequestBuilder;
import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.anEpdqCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.service.epdq.EpdqOrderRequestBuilder.anEpdqRefundOrderRequestBuilder;


public class EpdqPaymentProvider extends BasePaymentProvider<BaseResponse, String> {

    public static final String ROUTE_FOR_NEW_ORDER = "orderdirect.asp";
    public static final String ROUTE_FOR_MAINTENANCE_ORDER = "maintenancedirect.asp";

    private final SignatureGenerator signatureGenerator;
    private final String frontendUrl;

    public EpdqPaymentProvider(EnumMap<GatewayOperation, GatewayClient> clients, SignatureGenerator signatureGenerator,
                               ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator, String frontendUrl) {
        super(clients, externalRefundAvailabilityCalculator);
        this.signatureGenerator = signatureGenerator;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return PaymentGatewayName.EPDQ;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    @Override
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        return sendReceive(ROUTE_FOR_NEW_ORDER, request, buildAuthoriseOrderFor(frontendUrl), EpdqAuthorisationResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse<BaseResponse> authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        return responseBuilder()
                .withResponse(EpdqAuthorisationResponse.createPost3dsResponseFor(request.getAuth3DsDetails().getAuth3DResult())).build();
    }

    @Override
    public GatewayResponse capture(CaptureGatewayRequest request) {
        return sendReceive(ROUTE_FOR_MAINTENANCE_ORDER, request, buildCaptureOrderFor(), EpdqCaptureResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse refund(RefundGatewayRequest request) {
        return sendReceive(ROUTE_FOR_MAINTENANCE_ORDER, request, buildRefundOrderFor(), EpdqRefundResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse cancel(CancelGatewayRequest request) {
        return sendReceive(ROUTE_FOR_MAINTENANCE_ORDER, request, buildCancelOrderFor(), EpdqCancelResponse.class, extractResponseIdentifier());
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
    public boolean verifyNotification(Notification<String> notification, GatewayAccountEntity gatewayAccountEntity) {
        if (!notification.getPayload().isPresent()) return false;

        List<NameValuePair> notificationParams = notification.getPayload().get();

        List<NameValuePair> notificationParamsWithoutShaSign = notificationParams.stream()
                .filter(param -> !param.getName().equalsIgnoreCase(SHASIGN)).collect(toList());

        String signature = signatureGenerator.sign(notificationParamsWithoutShaSign, gatewayAccountEntity.getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE));

        return getShaSignFromNotificationParams(notificationParams).equalsIgnoreCase(signature);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }

    @Override
    public Either<String, Notifications<String>> parseNotification(String payload) {
        try {
            Notifications.Builder<String> builder = Notifications.builder();

            EpdqNotification epdqNotification = new EpdqNotification(payload);

            builder.addNotificationFor(
                    epdqNotification.getTransactionId(),
                    epdqNotification.getReference(),
                    epdqNotification.getStatus(),
                    null,
                    epdqNotification.getParams()
            );
            return right(builder.build());
        } catch (Exception e) {
            return left(e.getMessage());
        }
    }

    @Override
    public StatusMapper<String> getStatusMapper() {
        return EpdqStatusMapper.get();
    }

    private Function<AuthorisationGatewayRequest, GatewayOrder> buildAuthoriseOrderFor(String frontendUrl) {
        return request -> {
            EpdqOrderRequestBuilder epdqOrderRequestBuilder =
                    request.getGatewayAccount().isRequires3ds() ?
                            anEpdq3DsAuthoriseOrderRequestBuilder(frontendUrl) :
                            anEpdqAuthoriseOrderRequestBuilder();


            return epdqOrderRequestBuilder
                    .withOrderId(request.getChargeExternalId())
                    .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                    .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                            CREDENTIALS_SHA_IN_PASSPHRASE))
                    .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                    .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                    .withDescription(request.getDescription())
                    .withAmount(request.getAmount())
                    .withAuthorisationDetails(request.getAuthCardDetails())
                    .build();
        };
    }

    private Function<CaptureGatewayRequest, GatewayOrder> buildCaptureOrderFor() {
        return request -> anEpdqCaptureOrderRequestBuilder()
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private Function<CancelGatewayRequest, GatewayOrder> buildCancelOrderFor() {
        return request -> anEpdqCancelOrderRequestBuilder()
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private Function<RefundGatewayRequest, GatewayOrder> buildRefundOrderFor() {
        return request -> anEpdqRefundOrderRequestBuilder()
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .build();
    }

    private Function<GatewayClient.Response, Optional<String>> extractResponseIdentifier() {
        return response -> {
            Optional<String> emptyResponseIdentifierForEpdq = Optional.empty();
            return emptyResponseIdentifierForEpdq;
        };
    }

    public static BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> includeSessionIdentifier() {
        return (order, builder) -> builder;
    }

    private String getShaSignFromNotificationParams(List<NameValuePair> notificationParams) {
        return notificationParams.stream()
                .filter(param -> param.getName().equalsIgnoreCase(SHASIGN))
                .findFirst()
                .map(param -> param.getValue())
                .orElse("");
    }
}
