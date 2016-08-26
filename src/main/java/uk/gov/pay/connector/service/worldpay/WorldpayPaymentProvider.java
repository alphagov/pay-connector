package uk.gov.pay.connector.service.worldpay;

import fj.data.Either;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.resources.PaymentGatewayName;
import uk.gov.pay.connector.service.BasePaymentProvider;
import uk.gov.pay.connector.service.BaseResponse;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.StatusMapper;

import java.util.Optional;
import java.util.function.Function;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.service.OrderCaptureRequestBuilder.aWorldpayOrderCaptureRequest;
import static uk.gov.pay.connector.service.OrderRefundRequestBuilder.aWorldpayOrderRefundRequest;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aWorldpayOrderSubmitRequest;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderCancelRequestBuilder.aWorldpayOrderCancelRequest;
import static uk.gov.pay.connector.util.XMLUnmarshaller.unmarshall;

public class WorldpayPaymentProvider extends BasePaymentProvider<BaseResponse> {

    public WorldpayPaymentProvider(GatewayClient client) {
        super(client);
    }

    @Override
    public String getPaymentGatewayName() {
        return PaymentGatewayName.WORLDPAY.getName();
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.of(randomUUID().toString());
    }

    @Override
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        return sendReceive(request, buildAuthoriseOrderFor(), WorldpayOrderStatusResponse.class);
    }

    @Override
    public GatewayResponse capture(CaptureGatewayRequest request) {
        return sendReceive(request, buildCaptureOrderFor(), WorldpayCaptureResponse.class);
    }

    @Override
    public GatewayResponse refund(RefundGatewayRequest request) {
        return sendReceive(request, buildRefundOrderFor(), WorldpayRefundResponse.class);
    }

    @Override
    public GatewayResponse cancel(CancelGatewayRequest request) {
        return sendReceive(request, buildCancelOrderFor(), WorldpayCancelResponse.class);

    }

    @Override
    public Either<String, Notifications<String>> parseNotification(String payload) {
        try {
            Notifications.Builder<String> builder = Notifications.builder();
            WorldpayNotification worldpayNotification = unmarshall(payload, WorldpayNotification.class);
            builder.addNotificationFor(worldpayNotification.getTransactionId(), "", worldpayNotification.getStatus());
            return right(builder.build());
        } catch (Exception e) {
            return left(e.getMessage());
        }
    }

    @Override
    public StatusMapper getStatusMapper() {
        return WorldpayStatusMapper.get();
    }

    private Function<AuthorisationGatewayRequest, String> buildAuthoriseOrderFor() {
        return request -> aWorldpayOrderSubmitRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId().orElse(""))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withCard(request.getCard())
                .build();
    }

    private Function<CaptureGatewayRequest, String> buildCaptureOrderFor() {
        return request -> aWorldpayOrderCaptureRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .withDate(DateTime.now(DateTimeZone.UTC))
                .build();
    }

    private Function<RefundGatewayRequest, String> buildRefundOrderFor() {
        return request -> aWorldpayOrderRefundRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .build();
    }

    private Function<CancelGatewayRequest, String> buildCancelOrderFor() {
        return request -> aWorldpayOrderCancelRequest()
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .build();
    }
}
