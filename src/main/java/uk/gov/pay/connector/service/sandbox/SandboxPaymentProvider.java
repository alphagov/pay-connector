package uk.gov.pay.connector.service.sandbox;

import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.service.PaymentProvider;

import static uk.gov.pay.connector.model.CaptureResponse.aSuccessfulCaptureResponse;
import static uk.gov.pay.connector.model.GatewayErrorType.BaseGatewayError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.cardErrorFor;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.isInvalidCard;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.isValidCard;

public class SandboxPaymentProvider implements PaymentProvider {
    @Override
    public AuthorisationResponse authorise(AuthorisationRequest request) {

        String cardNumber = request.getCard().getCardNo();
        if (isInvalidCard(cardNumber)) {
            CardError errorInfo = cardErrorFor(cardNumber);
            return new AuthorisationResponse(false, new GatewayError(errorInfo.getErrorMessage(), BaseGatewayError), errorInfo.getNewErrorStatus());
        }

        if (isValidCard(cardNumber)) {
            return new AuthorisationResponse(true, null, AUTHORISATION_SUCCESS);
        }

        return new AuthorisationResponse(false, new GatewayError("Unsupported card details.", BaseGatewayError), null);
    }

    @Override
    public CaptureResponse capture(CaptureRequest request) {
        return aSuccessfulCaptureResponse();
    }
}
