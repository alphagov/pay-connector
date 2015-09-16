package uk.gov.pay.connector.service.sandbox;

import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.CardAuthorisationRequest;
import uk.gov.pay.connector.model.CardAuthorisationResponse;
import uk.gov.pay.connector.model.CardError;
import uk.gov.pay.connector.service.PaymentProvider;

import static uk.gov.pay.connector.model.CaptureResponse.aSuccessfulResponse;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.SandboxCardNumbers.cardErrorFor;
import static uk.gov.pay.connector.model.SandboxCardNumbers.isInvalidCard;
import static uk.gov.pay.connector.model.SandboxCardNumbers.isValidCard;

public class SandboxPaymentProvider implements PaymentProvider {
    @Override
    public CardAuthorisationResponse authorise(CardAuthorisationRequest request) {

        String cardNumber = request.getCard().getCardNo();
        if (isInvalidCard(cardNumber)) {
            CardError errorInfo = cardErrorFor(cardNumber);
            return new CardAuthorisationResponse(false, errorInfo.getErrorMessage(), errorInfo.getNewErrorStatus());
        }

        if (isValidCard(cardNumber)) {
            return new CardAuthorisationResponse(true, "", AUTHORISATION_SUCCESS);
        }

        return new CardAuthorisationResponse(false, "Unsupported card details.", null);
    }

    @Override
    public CaptureResponse capture(CaptureRequest request) {
        return aSuccessfulResponse();
    }
}
