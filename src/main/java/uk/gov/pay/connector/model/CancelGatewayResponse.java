package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ChargeStatus;

import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.FAILED;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.SUCCEDED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;

public class CancelGatewayResponse extends GatewayResponse {

    private final ChargeStatus status;

    public CancelGatewayResponse(ResponseStatus responseStatus, ErrorResponse errorMessage, ChargeStatus status) {
        this.responseStatus = responseStatus;
        this.error = errorMessage;
        this.status = status;
    }

    private CancelGatewayResponse(ErrorResponse error) {
        this.responseStatus = FAILED;
        this.error = error;
        this.status = SYSTEM_CANCEL_ERROR;
    }

    public static CancelGatewayResponse successfulCancelResponse(ChargeStatus status) {
        return new CancelGatewayResponse(SUCCEDED, null, status);
    }

    public static CancelGatewayResponse cancelFailureResponse(ErrorResponse errorResponse) {
        return new CancelGatewayResponse(errorResponse);
    }

    public ChargeStatus getStatus() {
        return status;
    }
}
