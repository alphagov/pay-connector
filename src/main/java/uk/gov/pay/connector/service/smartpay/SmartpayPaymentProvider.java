package uk.gov.pay.connector.service.smartpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.Notifications.Builder;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.resources.PaymentGatewayName;
import uk.gov.pay.connector.service.*;

import java.util.Optional;
import java.util.function.Function;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.service.OrderCaptureRequestBuilder.aSmartpayOrderCaptureRequest;
import static uk.gov.pay.connector.service.OrderRefundRequestBuilder.aSmartpayOrderRefundRequest;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aSmartpayOrderSubmitRequest;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderCancelRequestBuilder.aSmartpayOrderCancelRequest;

public class SmartpayPaymentProvider extends BasePaymentProvider<BaseResponse> {

    private final ObjectMapper objectMapper;

    public SmartpayPaymentProvider(GatewayClient client, ObjectMapper objectMapper) {
        super(client);
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
        return sendReceive(request, buildSubmitOrderFor(), SmartpayAuthorisationResponse.class);
    }

    @Override
    public GatewayResponse capture(CaptureGatewayRequest request) {
        return sendReceive(request, buildCaptureOrderFor(), SmartpayCaptureResponse.class);
    }

    @Override
    public GatewayResponse refund(RefundGatewayRequest request) {
        return sendReceive(request, buildRefundOrderFor(), SmartpayRefundResponse.class);
    }

    @Override
    public GatewayResponse cancel(CancelGatewayRequest request) {
        return sendReceive(request, buildCancelOrderFor(), SmartpayCancelResponse.class);

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
    public Either<String, Notifications<Pair<String, Boolean>>> parseNotification(String payload) {
        try {
            Builder<Pair<String, Boolean>> builder = Notifications.builder();

            objectMapper.readValue(payload, SmartpayNotificationList.class)
                    .getNotifications()
                    .forEach(notification -> builder.addNotificationFor(
                            notification.getOriginalReference(), notification.getPspReference(), notification.getStatus()));

            return right(builder.build());
        } catch (Exception e) {
            return left(e.getMessage());
        }
    }

    @Override
    public StatusMapper getStatusMapper() {
        return SmartpayStatusMapper.get();
    }

    private Function<AuthorisationGatewayRequest, GatewayOrder> buildSubmitOrderFor() {
        return request -> aSmartpayOrderSubmitRequest("authorisation")
                .withMerchantCode(getMerchantCode(request))
                .withPaymentPlatformReference(request.getChargeExternalId())
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withCard(request.getCard())
                .buildOrder();
    }

    private Function<CaptureGatewayRequest, GatewayOrder> buildCaptureOrderFor() {
        return request -> aSmartpayOrderCaptureRequest("capture")
                .withMerchantCode(getMerchantCode(request))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .buildOrder();
    }

    private Function<CancelGatewayRequest, GatewayOrder> buildCancelOrderFor() {
        return request -> aSmartpayOrderCancelRequest("cancel")
                .withMerchantCode(getMerchantCode(request))
                .withTransactionId(request.getTransactionId())
                .buildOrder();
    }

    private Function<RefundGatewayRequest, GatewayOrder> buildRefundOrderFor() {
        return request -> aSmartpayOrderRefundRequest("refund")
                .withMerchantCode(getMerchantCode(request))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .withReference(request.getReference())
                .buildOrder();
    }

    private String getMerchantCode(GatewayRequest request) {
        return request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID);
    }
}
