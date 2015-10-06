package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.*;

public interface PaymentProvider {

    AuthorisationResponse authorise(AuthorisationRequest request);

    CaptureResponse capture(CaptureRequest request);

    CancelResponse cancel(CancelRequest request);

    StatusResponse enquire(ChargeStatusRequest request);
}
