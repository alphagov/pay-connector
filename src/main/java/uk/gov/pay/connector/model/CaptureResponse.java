package uk.gov.pay.connector.model;

import org.slf4j.Logger;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.baseError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_ERROR;

public class CaptureResponse implements GatewayResponse {

    private final Boolean successful;
    private final ErrorResponse error;

    private ChargeStatus status;

    public CaptureResponse(Boolean successful, ErrorResponse error, ChargeStatus status) {
        this.successful = successful;
        this.error = error;
        this.status = status;
    }

    public CaptureResponse(ErrorResponse error) {
        this.successful = false;
        this.error = error;
        this.status = CAPTURE_ERROR;
    }

    public ErrorResponse getError() {
        return error;
    }

    public Boolean isInProgress() {
        return false;
    }

    public static CaptureResponse successfulCaptureResponse(ChargeStatus status) {
        return new CaptureResponse(true, null, status);
    }

    public static CaptureResponse captureFailureResponse(Logger logger, String errorMessage, String transactionId) {
        logger.error(format("Failed to capture for transaction id %s: %s", transactionId, errorMessage));
        return new CaptureResponse(false, baseError(errorMessage), CAPTURE_ERROR);
    }

    public static CaptureResponse captureFailureResponse(ErrorResponse errorResponse) {
        return new CaptureResponse(false, errorResponse, CAPTURE_ERROR);
    }

    public static CaptureResponse captureFailureResponse(Logger logger, String errorMessage) {
        logger.error(format("Error processing capture request : %s", errorMessage));
        return new CaptureResponse(false, baseError(errorMessage), CAPTURE_ERROR);
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public ChargeStatus getStatus() {
        return status;
    }
}
