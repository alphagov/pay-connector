package uk.gov.pay.connector.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.baseError;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.FAILED;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.SUCCEDED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_ERROR;

public class CaptureGatewayResponse extends GatewayResponse {

    static private final Logger logger = LoggerFactory.getLogger(CaptureGatewayResponse.class);

    private ChargeStatus status;

    public CaptureGatewayResponse(ResponseStatus responseStatus, ChargeStatus status) {
        this.responseStatus = responseStatus;
        this.status = status;
    }

    public CaptureGatewayResponse(ErrorResponse error) {
        this.responseStatus = FAILED;
        this.error = error;
        this.status = CAPTURE_ERROR;
    }

    public static CaptureGatewayResponse successfulCaptureResponse(ChargeStatus status) {
        return new CaptureGatewayResponse(SUCCEDED, status);
    }

    public static CaptureGatewayResponse captureFailureResponse(String errorMessage, String transactionId) {
        logger.error(format("Failed to capture for transaction id %s: %s", transactionId, errorMessage));
        return new CaptureGatewayResponse(baseError(errorMessage));
    }

    public static CaptureGatewayResponse captureFailureResponse(ErrorResponse errorResponse) {
        return new CaptureGatewayResponse(errorResponse);
    }

    public static CaptureGatewayResponse captureFailureResponse(String errorMessage) {
        logger.error(format("Error processing capture request : %s", errorMessage));
        return new CaptureGatewayResponse(baseError(errorMessage));
    }

    public ChargeStatus getStatus() {
        return status;
    }
}
