package uk.gov.pay.connector.model;

import org.slf4j.Logger;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.baseError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;

public class AuthorisationResponse implements GatewayResponse {

    private boolean successful;
    private ErrorResponse error;
    private ChargeStatus status;
    private String transactionId;
    private boolean inProgress;

    public AuthorisationResponse(boolean successful, ErrorResponse error, ChargeStatus status, String transactionId) {
        this(successful, error, status, transactionId, false);
    }

    public AuthorisationResponse(boolean successful, ErrorResponse error, ChargeStatus status, String transactionId, boolean inProgress) {
        this.successful = successful;
        this.error = error;
        this.status = status;
        this.transactionId = transactionId;
        this.inProgress = inProgress;
    }

    public AuthorisationResponse(ErrorResponse error) {
        this.successful = false;
        this.error = error;
        this.status = AUTHORISATION_ERROR;
    }

    public static AuthorisationResponse successfulAuthorisationResponse(ChargeStatus status, String transactionId) {
        return new AuthorisationResponse(true, null, status, transactionId);
    }

    public static AuthorisationResponse authorisationFailureNotUpdateResponse(Logger logger, String transactionId, String errorMessage) {
        logger.error(format("Error received from gateway: %s.", errorMessage));
        return new AuthorisationResponse(false, baseError(errorMessage), AUTHORISATION_ERROR, transactionId);
    }

    public static AuthorisationResponse authorisationFailureResponse(Logger logger, String transactionId, String errorMessage) {
        logger.error(format("Failed to authorise transaction with id %s: %s", transactionId, errorMessage));
        return new AuthorisationResponse(false, baseError("This transaction was declined."), AUTHORISATION_REJECTED, transactionId);
    }

    public static AuthorisationResponse authorisationFailureResponse(ErrorResponse errorResponse) {
        return new AuthorisationResponse(errorResponse);
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public ErrorResponse getError() {
        return error;
    }

    @Override
    public Boolean isInProgress() {
        return inProgress;
    }

    public ChargeStatus getNewChargeStatus() {
        return status;
    }

    public String getTransactionId() {
        return transactionId;
    }
}
