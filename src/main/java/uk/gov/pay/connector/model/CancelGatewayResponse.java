package uk.gov.pay.connector.model;

import org.slf4j.Logger;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.baseError;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.FAILED;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.SUCCEDED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;

public class CancelGatewayResponse extends GatewayResponse {

    private final ErrorResponse error;

    private final ChargeStatus status;

    public CancelGatewayResponse(ResponseStatus responseStatus, ErrorResponse errorMessage, ChargeStatus status) {
        this.responseStatus = responseStatus;
        this.error = errorMessage;
        this.status = status;
    }

    public CancelGatewayResponse(ErrorResponse error) {
        this.responseStatus = FAILED;
        this.error = error;
        this.status = SYSTEM_CANCEL_ERROR;
    }

    public static CancelGatewayResponse successfulCancelResponse(ChargeStatus status) {
        return new CancelGatewayResponse(SUCCEDED, null, status);
    }

    public static CancelGatewayResponse cancelFailureResponse(Logger logger, String errorMessage) {
        logger.error(format("Failed to cancel charge: %s", errorMessage));
        return new CancelGatewayResponse(FAILED, baseError(errorMessage), SYSTEM_CANCEL_ERROR);
    }

    public static CancelGatewayResponse cancelFailureResponse(ErrorResponse errorResponse) {
        return new CancelGatewayResponse(errorResponse);
    }

    public ErrorResponse getError() {
        return error;
    }

    public ChargeStatus getStatus() {
        return status;
    }
}
