package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.CardAuthorisationRequest;
import uk.gov.pay.connector.model.CardAuthorisationResponse;
import uk.gov.pay.connector.model.GatewayAccount;

public interface PaymentProvider {

    CardAuthorisationResponse authorise(CardAuthorisationRequest request);

    CaptureResponse capture(CaptureRequest request);

}
