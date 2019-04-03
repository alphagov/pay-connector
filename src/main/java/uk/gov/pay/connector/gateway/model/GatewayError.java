package uk.gov.pay.connector.gateway.model;

import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;

public class GatewayError {

    private final String message;
    private final ErrorType errorType;

    public GatewayError(String message, ErrorType errorType) {
        this.message = message;
        this.errorType = errorType;
    }

    public static GatewayError gatewayConnectionError(String msg) {
        return new GatewayError(msg, GATEWAY_ERROR);
    }

    public static GatewayError genericGatewayError(String msg) {
        return new GatewayError(msg, GENERIC_GATEWAY_ERROR);
    }

    public String getMessage() {
        return message;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    @Override
    public String toString() {
        return "Gateway error: " + message;
    }
}
