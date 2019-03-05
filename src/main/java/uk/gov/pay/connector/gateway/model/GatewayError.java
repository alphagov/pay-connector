package uk.gov.pay.connector.gateway.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;

public class GatewayError {
    private String message;
    private ErrorType errorType;

    public GatewayError(String message, ErrorType errorType) {
        this.message = message;
        this.errorType = errorType;
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
