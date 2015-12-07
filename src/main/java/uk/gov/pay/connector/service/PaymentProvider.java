package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ServiceAccount;

import java.util.Optional;

//FIXME: possible refactoring
public interface PaymentProvider {

    AuthorisationResponse authorise(AuthorisationRequest request);

    CaptureResponse capture(CaptureRequest request);

    CancelResponse cancel(CancelRequest request);

    StatusUpdates newStatusFromNotification(ServiceAccount serviceAccount, String transactionId);

    Optional<String> getNotificationTransactionId(String inboundNotification);
}
