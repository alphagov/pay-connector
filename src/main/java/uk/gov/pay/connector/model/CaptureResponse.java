package uk.gov.pay.connector.model;

import org.slf4j.Logger;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.baseGatewayError;

public class CaptureResponse implements GatewayResponse {

    private final Boolean successful;
    private final ErrorResponse error;


    public CaptureResponse(boolean successful, ErrorResponse errorMessage) {
        this.successful = successful;
        this.error = errorMessage;
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public ErrorResponse getError() {
        return error;
    }

    public static CaptureResponse aSuccessfulCaptureResponse() {
        return new CaptureResponse(true, null);
    }

    public static CaptureResponse captureFailureResponse(Logger logger, String errorMessage, String transactionId) {
        logger.error(format("Failed to capture for transaction id %s: %s", transactionId, errorMessage));

        return new CaptureResponse(false, baseGatewayError("A problem occurred."));
    }

    public static CaptureResponse captureFailureResponse(ErrorResponse errorResponse) {
        return new CaptureResponse(false, errorResponse);
    }

    public static CaptureResponse captureFailureResponse(Logger logger, String errorMessage) {
        logger.error(format("Error processing capture request : %s", errorMessage));
        return new CaptureResponse(false, baseGatewayError(errorMessage));
    }
}
