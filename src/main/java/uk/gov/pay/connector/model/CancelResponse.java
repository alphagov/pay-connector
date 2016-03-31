package uk.gov.pay.connector.model;

import org.slf4j.Logger;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.baseError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CANCEL_ERROR;

public class CancelResponse implements GatewayResponse {

    private final Boolean successful;
    private final ErrorResponse error;

    private final ChargeStatus status;

    public CancelResponse(boolean successful, ErrorResponse errorMessage, ChargeStatus status) {
        this.successful = successful;
        this.error = errorMessage;
        this.status = status;
    }

    public CancelResponse(ErrorResponse error) {
        this.successful = false;
        this.error = error;
        this.status = CANCEL_ERROR;
    }

    public static CancelResponse successfulCancelResponse(ChargeStatus status) {
        return new CancelResponse(true, null, status);
    }

    public static CancelResponse cancelFailureResponse(Logger logger, String errorMessage) {
        logger.error(format("Failed to cancel charge: %s", errorMessage));
        return new CancelResponse(false, baseError(errorMessage), CANCEL_ERROR);
    }

    public static CancelResponse cancelFailureResponse(ErrorResponse errorResponse) {
        return new CancelResponse(errorResponse);
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public ErrorResponse getError() {
        return error;
    }

    @Override
    public Boolean isInProgress() {
        return false;
    }

    public ChargeStatus getStatus() {
        return status;
    }

}
