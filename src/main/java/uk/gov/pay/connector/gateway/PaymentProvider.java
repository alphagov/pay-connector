package uk.gov.pay.connector.gateway;

import fj.data.Either;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.Notification;
import uk.gov.pay.connector.usernotification.model.Notifications;

public interface PaymentProvider<T extends BaseResponse, R> {

    PaymentGatewayName getPaymentGatewayName();

    StatusMapper getStatusMapper();
    
    GatewayResponse<T> capture(CaptureGatewayRequest request);

    GatewayResponse<T> refund(RefundGatewayRequest request);

    GatewayResponse<T> cancel(CancelGatewayRequest request);

    Either<String, Notifications<R>> parseNotification(String payload);

    Boolean isNotificationEndpointSecured();

    String getNotificationDomain();

    boolean verifyNotification(Notification<R> notification, GatewayAccountEntity gatewayAccountEntity);

    ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity);
}
