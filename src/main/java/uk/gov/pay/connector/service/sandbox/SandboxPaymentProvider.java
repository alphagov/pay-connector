package uk.gov.pay.connector.service.sandbox;

import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.service.PaymentProvider;

import java.util.UUID;

import static uk.gov.pay.connector.model.CancelResponse.aSuccessfulCancelResponse;
import static uk.gov.pay.connector.model.CaptureResponse.aSuccessfulCaptureResponse;
import static uk.gov.pay.connector.model.GatewayErrorType.GenericGatewayError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.*;

public class SandboxPaymentProvider implements PaymentProvider {
    @Override
    public AuthorisationResponse authorise(AuthorisationRequest request) {

        String cardNumber = request.getCard().getCardNo();
        String transactionId = UUID.randomUUID().toString();

        if (isInvalidCard(cardNumber)) {
            CardError errorInfo = cardErrorFor(cardNumber);
            return new AuthorisationResponse(false, new GatewayError(errorInfo.getErrorMessage(), GenericGatewayError), errorInfo.getNewErrorStatus(), transactionId);
        }

        if (isValidCard(cardNumber)) {
            return new AuthorisationResponse(true, null, AUTHORISATION_SUCCESS, transactionId);
        }

        return new AuthorisationResponse(false, new GatewayError("Unsupported card details.", GenericGatewayError), null, transactionId);
    }

    @Override
    public CaptureResponse capture(CaptureRequest request) {
        return aSuccessfulCaptureResponse();
    }

    @Override
    public CancelResponse cancel(CancelRequest request) {
        return aSuccessfulCancelResponse();
    }

    @Override
    public StatusUpdates newStatusFromNotification(String notification) {
        // No notifications for the sandbox.
        return StatusUpdates.noUpdate("OK");
    }
}
