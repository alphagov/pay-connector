package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface PaymentProvider {

    AuthorisationResponse authorise(AuthorisationRequest request);

    CaptureResponse capture(CaptureRequest request);

    CancelGatewayResponse cancel(CancelRequest request);

    StatusUpdates handleNotification(String notificationPayload, Function<ChargeStatusRequest, Boolean> payloadChecks, Function<String, Optional<GatewayAccountEntity>> accountFinder, Consumer<StatusUpdates> accountUpdater);
}
