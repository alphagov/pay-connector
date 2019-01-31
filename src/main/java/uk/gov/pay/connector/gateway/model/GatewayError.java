package uk.gov.pay.connector.gateway.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.stripe.GatewayException;

import java.net.SocketTimeoutException;

import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_ERROR;

public class GatewayError {
    private String message;
    private ErrorType errorType;

    private static final Logger logger = LoggerFactory.getLogger(GatewayError.class);

    public GatewayError(String message, ErrorType errorType) {
        this.message = message;
        this.errorType = errorType;
    }

    public static GatewayError gatewayConnectionError(String msg) {
        return new GatewayError(msg, GATEWAY_CONNECTION_ERROR);
    }

    public static GatewayError genericGatewayError(String msg) {
        return new GatewayError(msg, GENERIC_GATEWAY_ERROR);
    }

    public static GatewayError gatewayConnectionTimeoutException(String msg) {
        return new GatewayError(msg, GATEWAY_CONNECTION_TIMEOUT_ERROR);
    }

    public static GatewayError of(GatewayException e) {

        GatewayError gatewayError;

        if (e.getCause() != null) {
            if (e.getCause() instanceof SocketTimeoutException) {
                logger.error(format("Connection timed out error for gateway url=%s", e.getUrl()), e);
                gatewayError = GatewayError.gatewayConnectionTimeoutException("Gateway connection timeout error");
            } else {
                gatewayError = GatewayError.genericGatewayError(e.getMessage());
            }
        } else {
            gatewayError = GatewayError.genericGatewayError(e.getMessage());
        }

        return gatewayError;
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
