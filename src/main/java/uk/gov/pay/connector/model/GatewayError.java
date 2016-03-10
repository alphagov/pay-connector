package uk.gov.pay.connector.model;

import static uk.gov.pay.connector.model.GatewayErrorType.*;

public class GatewayError {
    private String message;
    private GatewayErrorType errorType;

    public GatewayError(String message, GatewayErrorType errorType) {
        this.message = message;
        this.errorType = errorType;
    }

    public static GatewayError baseGatewayError(String msg) {
        return new GatewayError(msg, GENERIC_GATEWAY_ERROR);
    }

    public static GatewayError unexpectedStatusCodeFromGateway(String msg) {
        return new GatewayError(msg, UNEXPECTED_STATUS_CODE_FROM_GATEWAY);
    }

    public static GatewayError malformedResponseReceivedFromGateway(String msg) {
        return new GatewayError(msg, MALFORMED_RESPONSE_RECEIVED_FROM_GATEWAY);
    }

    public static GatewayError unknownHostException(String msg) {
        return new GatewayError(msg, GATEWAY_URL_DNS_ERROR);
    }

    public static GatewayError gatewayConnectionTimeoutException(String msg) {
        return new GatewayError(msg, GATEWAY_CONNECTION_TIMEOUT_ERROR);
    }

    public static GatewayError illegalStateError(String msg) {
        return new GatewayError(msg, ILLEGAL_STATE_ERROR);
    }

    public static GatewayError conflictError(String msg) {
        return new GatewayError(msg, CONFLICT_ERROR);
    }

    public static GatewayError gatewayConnectionSocketException(String msg) {
        return new GatewayError(msg, GATEWAY_CONNECTION_SOCKET_ERROR);
    }

    public static GatewayError operationAlreadyInProgress(String msg) {
        return new GatewayError(msg, OPERATION_ALREADY_IN_PROGRESS);
    }

    public String getMessage() {
        return message;
    }

    public GatewayErrorType getErrorType() {
        return errorType;
    }
}
