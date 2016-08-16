package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface PaymentProvider<T extends BaseResponse> {

    Optional<String> generateTransactionId();

    GatewayResponse<T> authorise(AuthorisationGatewayRequest request);

    GatewayResponse<T> capture(CaptureGatewayRequest request);

    GatewayResponse<T> refund(RefundGatewayRequest request);

    GatewayResponse<T> cancel(CancelGatewayRequest request);

    GatewayResponse<T> inquire(String transactionId, GatewayAccountEntity gatewayAccount);

    StatusUpdates handleNotification(String notificationPayload,
                                     Function<ChargeStatusRequest, Boolean> payloadChecks,
                                     Function<String, Optional<GatewayAccountEntity>> accountFinder,
                                     Consumer<StatusUpdates> accountUpdater);
}
