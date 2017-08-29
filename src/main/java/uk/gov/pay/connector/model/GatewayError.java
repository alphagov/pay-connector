package uk.gov.pay.connector.model;

import static uk.gov.pay.connector.model.ErrorType.*;

public class GatewayError {
    private String message;
    private ErrorType errorType;

    public GatewayError(String message, ErrorType errorType) {
        this.message = message;
        this.errorType = errorType;
    }

    public static GatewayError baseError(String msg) {
        return new GatewayError(msg, GENERIC_GATEWAY_ERROR);
    }

    public static GatewayError unexpectedStatusCodeFromGateway(String msg) {
        return new GatewayError(msg, UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY);
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

    public static GatewayError gatewayConnectionSocketException(String msg) {
        return new GatewayError(msg, GATEWAY_CONNECTION_SOCKET_ERROR);
    }

    public String getMessage() {
        return message;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    @Override
    public String toString() {
        return "GatewayError{" +
                "message='" + message + '\'' +
                ", errorType=" + errorType +
                '}';
    }
}
