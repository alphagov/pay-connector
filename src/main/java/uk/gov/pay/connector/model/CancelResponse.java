package uk.gov.pay.connector.model;

import org.slf4j.Logger;

import javax.ws.rs.core.Response;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.GatewayError.baseGatewayError;

public class CancelResponse implements GatewayResponse {

    private final Boolean successful;
    private final GatewayError error;


    public CancelResponse(boolean successful, GatewayError errorMessage) {
        this.successful = successful;
        this.error = errorMessage;
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public GatewayError getError() {
        return error;
    }

    public static CancelResponse aSuccessfulCancelResponse() {
        return new CancelResponse(true, null);
    }

    public static CancelResponse errorCancelResponse(Logger logger, Response response) {
        logger.error(format("Error code received from gateway: %s.", response.getStatus()));
        return new CancelResponse(false, baseGatewayError("Error processing request"));
    }

    public static CancelResponse cancelFailureResponse(Logger logger, String errorMessage) {
        logger.error(format("Failed to cancel charge: %s", errorMessage));
        return new CancelResponse(false, baseGatewayError(errorMessage));
    }

    public static CancelResponse cancelFailureResponse(GatewayError gatewayError) {
        return new CancelResponse(false, gatewayError);
    }
}
