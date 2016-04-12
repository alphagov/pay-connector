package uk.gov.pay.connector.model;

import org.slf4j.Logger;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.baseError;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.FAILED;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.SUCCEDED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CANCEL_ERROR;

public class CancelResponse extends GatewayResponse {

    private final ErrorResponse error;

    private final ChargeStatus status;

    public CancelResponse(ResponseStatus responseStatus, ErrorResponse errorMessage, ChargeStatus status) {
        this.responseStatus = responseStatus;
        this.error = errorMessage;
        this.status = status;
    }

    public CancelResponse(ErrorResponse error) {
        this.responseStatus = FAILED;
        this.error = error;
        this.status = CANCEL_ERROR;
    }

    public static CancelResponse successfulCancelResponse(ChargeStatus status) {
        return new CancelResponse(SUCCEDED, null, status);
    }

    public static CancelResponse cancelFailureResponse(Logger logger, String errorMessage) {
        logger.error(format("Failed to cancel charge: %s", errorMessage));
        return new CancelResponse(FAILED, baseError(errorMessage), CANCEL_ERROR);
    }

    public static CancelResponse cancelFailureResponse(ErrorResponse errorResponse) {
        return new CancelResponse(errorResponse);
    }

    public ErrorResponse getError() {
        return error;
    }

    public ChargeStatus getStatus() {
        return status;
    }

}
