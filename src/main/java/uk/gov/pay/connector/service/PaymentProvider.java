package uk.gov.pay.connector.service;

import fj.data.Either;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import java.util.Optional;

public interface PaymentProvider<T extends BaseResponse> {

    String getPaymentProviderName();

    Optional<String> generateTransactionId();

    GatewayResponse<T> authorise(AuthorisationGatewayRequest request);

    GatewayResponse<T> capture(CaptureGatewayRequest request);

    GatewayResponse<T> refund(RefundGatewayRequest request);

    GatewayResponse<T> cancel(CancelGatewayRequest request);

    <R> Either<String, Notifications<R>> parseNotification(String payload);

    StatusMapper getStatusMapper();
}
