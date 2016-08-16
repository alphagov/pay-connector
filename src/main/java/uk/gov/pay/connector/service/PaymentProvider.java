package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.gateway.*;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface PaymentProvider<T extends BaseResponse> {

    Optional<String> generateTransactionId();

    GatewayResponse<T> authorise(AuthorisationGatewayRequest request);

    GatewayResponse<T> capture(CaptureGatewayRequest request);

    GatewayResponse<T> refund(RefundGatewayRequest request);

    GatewayResponse<T> cancel(CancelGatewayRequest request);

    GatewayResponse<T> inquire(InquiryGatewayRequest request);

    StatusUpdates handleNotification(String notificationPayload,
                                     Function<ChargeStatusRequest, Boolean> payloadChecks,
                                     Function<String, Optional<ChargeEntity>> accountFinder,
                                     Consumer<StatusUpdates> accountUpdater);
}
