package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface PaymentProvider {

    AuthorisationGatewayResponse authorise(AuthorisationGatewayRequest request);

    CaptureGatewayResponse capture(CaptureGatewayRequest request);

    CancelGatewayResponse cancel(CancelGatewayRequest request);

    RefundGatewayResponse refund(RefundGatewayRequest request);

    StatusUpdates handleNotification(String notificationPayload, Function<ChargeStatusRequest, Boolean> payloadChecks, Function<String, Optional<GatewayAccountEntity>> accountFinder, Consumer<StatusUpdates> accountUpdater);
}
