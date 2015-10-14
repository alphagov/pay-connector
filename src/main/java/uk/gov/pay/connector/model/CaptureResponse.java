package uk.gov.pay.connector.model;

import org.slf4j.Logger;

import javax.ws.rs.core.Response;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.GatewayError.baseGatewayError;
import static uk.gov.pay.connector.model.GatewayError.unexpectedStatusCodeFromGateway;

public class CaptureResponse implements GatewayResponse {

    private final Boolean successful;
    private final GatewayError error;


    public CaptureResponse(boolean successful, GatewayError errorMessage) {
        this.successful = successful;
        this.error = errorMessage;
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public GatewayError getError() {
        return error;
    }

    public static CaptureResponse aSuccessfulCaptureResponse() {
        return new CaptureResponse(true, null);
    }

    public static CaptureResponse captureFailureResponse(Logger logger, String errorMessage, String transactionId) {
        logger.error(format("Failed to capture for transaction id %s: %s", transactionId, errorMessage));

        return new CaptureResponse(false, baseGatewayError(errorMessage));
    }

    public static CaptureResponse errorCaptureResponse(Logger logger, Response response) {
        logger.error(format("Error code received from provider: response status = %s.", response.getStatus()));
        return new CaptureResponse(false, unexpectedStatusCodeFromGateway("Error processing capture request"));
    }

    public static CaptureResponse captureFailureResponse(GatewayError gatewayError) {
        return new CaptureResponse(false, gatewayError);
    }
}
