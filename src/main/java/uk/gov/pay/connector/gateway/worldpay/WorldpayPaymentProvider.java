package uk.gov.pay.connector.gateway.worldpay;

import fj.data.Either;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import uk.gov.pay.connector.gateway.BasePaymentProvider;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.StatusMapper;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.ws.rs.client.Invocation.Builder;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpay3dsResponseAuthOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayRefundOrderRequestBuilder;

public class WorldpayPaymentProvider extends BasePaymentProvider<BaseResponse, String> {

    public static final String WORLDPAY_MACHINE_COOKIE_NAME = "machine";

    public WorldpayPaymentProvider(EnumMap<GatewayOperation, GatewayClient> clients, boolean isNotificationEndpointSecured, String notificationDomain,
                                   ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator) {
        super(clients, isNotificationEndpointSecured, notificationDomain, externalRefundAvailabilityCalculator);
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return PaymentGatewayName.WORLDPAY;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.of(randomUUID().toString());
    }

    @Override
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        return sendReceive(request, buildAuthoriseOrderFor(), WorldpayOrderStatusResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse<BaseResponse> authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        return sendReceive(request, build3dsResponseAuthOrderFor(), WorldpayOrderStatusResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse capture(CaptureGatewayRequest request) {
        return sendReceive(request, buildCaptureOrderFor(), WorldpayCaptureResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse refund(RefundGatewayRequest request) {
        return sendReceive(request, buildRefundOrderFor(), WorldpayRefundResponse.class, extractResponseIdentifier());
    }

    @Override
    public GatewayResponse cancel(CancelGatewayRequest request) {
        return sendReceive(request, buildCancelOrderFor(), WorldpayCancelResponse.class, extractResponseIdentifier());

    }

    @Override
    public Boolean isNotificationEndpointSecured() {
        return this.isNotificationEndpointSecured;
    }

    @Override
    public String getNotificationDomain() {
        return this.notificationDomain;
    }

    @Override
    public boolean verifyNotification(Notification<String> notification, GatewayAccountEntity gatewayAccountEntity) {
        return true;
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }

    @Override
    public Either<String, Notifications<String>> parseNotification(String payload) {
        try {
            Notifications.Builder<String> builder = Notifications.builder();
            WorldpayNotification worldpayNotification = XMLUnmarshaller.unmarshall(payload, WorldpayNotification.class);
            builder.addNotificationFor(
                worldpayNotification.getTransactionId(),
                worldpayNotification.getReference(),
                worldpayNotification.getStatus(),
                worldpayNotification.getBookingDate().atStartOfDay(ZoneOffset.UTC),
                    null
            );

            return right(builder.build());
        } catch (Exception e) {
            return left(e.getMessage());
        }
    }

    @Override
    public StatusMapper<String> getStatusMapper() {
        return WorldpayStatusMapper.get();
    }

    public static BiFunction<GatewayOrder, Builder, Builder> includeSessionIdentifier() {
        return (order, builder) ->
            order.getProviderSessionId()
                    .map(sessionId -> builder.cookie(WORLDPAY_MACHINE_COOKIE_NAME, sessionId))
                    .orElseGet(() -> builder);
    }

    private Function<AuthorisationGatewayRequest, GatewayOrder> buildAuthoriseOrderFor() {
        return request -> aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(request.getChargeExternalId())
                .with3dsRequired(request.getGatewayAccount().isRequires3ds())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails())
                .build();
    }

    private Function<Auth3dsResponseGatewayRequest, GatewayOrder> build3dsResponseAuthOrderFor() {
        return request -> aWorldpay3dsResponseAuthOrderRequestBuilder()
                .withPaResponse3ds(request.getAuth3DsDetails().getPaResponse())
                .withSessionId(request.getChargeExternalId())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withProviderSessionId(request.getProviderSessionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private Function<CaptureGatewayRequest, GatewayOrder> buildCaptureOrderFor() {
        return request -> aWorldpayCaptureOrderRequestBuilder()
                .withDate(DateTime.now(DateTimeZone.UTC))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private Function<RefundGatewayRequest, GatewayOrder> buildRefundOrderFor() {
        return request -> aWorldpayRefundOrderRequestBuilder()
                .withReference(request.getReference())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .withTransactionId(request.getTransactionId())
                .build();
    }

    private Function<CancelGatewayRequest, GatewayOrder> buildCancelOrderFor() {
        return request -> aWorldpayCancelOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private Function<GatewayClient.Response, Optional<String>> extractResponseIdentifier() {
        return response -> Optional.ofNullable(response.getResponseCookies().get(WORLDPAY_MACHINE_COOKIE_NAME));
    }
}
