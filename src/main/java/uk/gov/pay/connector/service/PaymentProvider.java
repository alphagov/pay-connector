package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ServiceAccount;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface PaymentProvider {

    AuthorisationResponse authorise(AuthorisationRequest request);

    CaptureResponse capture(CaptureRequest request);

    CancelResponse cancel(CancelRequest request);

    StatusUpdates handleNotification(String notificationPayload, Function<String, ServiceAccount> accountFinder, Consumer<StatusUpdates> accountUpdater);
}
