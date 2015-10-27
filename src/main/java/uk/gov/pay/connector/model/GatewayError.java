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
        return new GatewayError(msg, GenericGatewayError);
    }

    public static GatewayError unexpectedStatusCodeFromGateway(String msg) {
        return new GatewayError(msg, UnexpectedStatusCodeFromGateway);
    }

    public static GatewayError malformedResponseReceivedFromGateway(String msg) {
        return new GatewayError(msg, MalformedResponseReceivedFromGateway);
    }

    public static GatewayError unknownHostException(String msg) {
        return new GatewayError(msg, GatewayUrlDnsError);
    }

    public static GatewayError gatewayConnectionTimeoutException(String msg) {
        return new GatewayError(msg, GatewayConnectionTimeoutError);
    }

    public static GatewayError gatewayConnectionSocketException(String msg) {
        return new GatewayError(msg, GatewayConnectionSocketError);
    }

    public String getMessage() {
        return message;
    }

    public GatewayErrorType getErrorType() {
        return errorType;
    }
}
