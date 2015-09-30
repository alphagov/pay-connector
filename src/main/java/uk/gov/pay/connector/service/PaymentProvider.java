package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.CancelRequest;
import uk.gov.pay.connector.model.CancelResponse;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;

public interface PaymentProvider {

    AuthorisationResponse authorise(AuthorisationRequest request);

    CaptureResponse capture(CaptureRequest request);

    CancelResponse cancel(CancelRequest request);
}
