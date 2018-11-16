package uk.gov.pay.connector.gateway.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.stripe.GatewayException;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_SOCKET_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_URL_DNS_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.MALFORMED_RESPONSE_RECEIVED_FROM_GATEWAY;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;

public class GatewayError {
    private String message;
    private ErrorType errorType;

    private static final Logger logger = LoggerFactory.getLogger(GatewayError.class);

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

    public static GatewayError of(GatewayException e) {

        GatewayError gatewayError;

        if (e.getCause() != null) {
            if (e.getCause() instanceof UnknownHostException) {
                logger.error(format("DNS resolution error for gateway url=%s", e.getUrl()), e);
                gatewayError = GatewayError.unknownHostException("Gateway Url DNS resolution error");
            } else if (e.getCause() instanceof SocketTimeoutException) {
                logger.error(format("Connection timed out error for gateway url=%s", e.getUrl()), e);
                gatewayError = GatewayError.gatewayConnectionTimeoutException("Gateway connection timeout error");
            } else if (e.getCause() instanceof SocketException) {
                logger.error(format("Socket Exception for gateway url=%s", e.getUrl()), e);
                gatewayError = GatewayError.gatewayConnectionSocketException("Gateway connection socket error");
            } else {
                gatewayError = GatewayError.baseError(e.getMessage());
            }
        } else {
            gatewayError = GatewayError.baseError(e.getMessage());
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
