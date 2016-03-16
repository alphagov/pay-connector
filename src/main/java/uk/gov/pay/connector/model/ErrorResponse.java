package uk.gov.pay.connector.model;

import static uk.gov.pay.connector.model.ErrorType.*;

public class ErrorResponse {
    private String message;
    private ErrorType errorType;

    public ErrorResponse(String message, ErrorType errorType) {
        this.message = message;
        this.errorType = errorType;
    }

    public static ErrorResponse baseGatewayError(String msg) {
        return new ErrorResponse(msg, GENERIC_GATEWAY_ERROR);
    }

    public static ErrorResponse unexpectedStatusCodeFromGateway(String msg) {
        return new ErrorResponse(msg, UNEXPECTED_STATUS_CODE_FROM_GATEWAY);
    }

    public static ErrorResponse malformedResponseReceivedFromGateway(String msg) {
        return new ErrorResponse(msg, MALFORMED_RESPONSE_RECEIVED_FROM_GATEWAY);
    }

    public static ErrorResponse unknownHostException(String msg) {
        return new ErrorResponse(msg, GATEWAY_URL_DNS_ERROR);
    }

    public static ErrorResponse gatewayConnectionTimeoutException(String msg) {
        return new ErrorResponse(msg, GATEWAY_CONNECTION_TIMEOUT_ERROR);
    }

    public static ErrorResponse illegalStateError(String msg) {
        return new ErrorResponse(msg, ILLEGAL_STATE_ERROR);
    }

    public static ErrorResponse conflictError(String msg) {
        return new ErrorResponse(msg, CONFLICT_ERROR);
    }

    public static ErrorResponse gatewayConnectionSocketException(String msg) {
        return new ErrorResponse(msg, GATEWAY_CONNECTION_SOCKET_ERROR);
    }

    public static ErrorResponse operationAlreadyInProgress(String msg) {
        return new ErrorResponse(msg, OPERATION_ALREADY_IN_PROGRESS);
    }

    public static ErrorResponse chargeExpired(String msg) {
        return new ErrorResponse(msg, CHARGE_EXPIRED);
    }

    public String getMessage() {
        return message;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "message='" + message + '\'' +
                ", errorType=" + errorType +
                '}';
    }
}
