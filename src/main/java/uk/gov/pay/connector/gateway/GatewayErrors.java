package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.GatewayError;

public class GatewayErrors {

    public interface ToGatewayError {
        GatewayError toGatewayError();
    }

    public static class GenericGatewayErrorException extends Exception implements ToGatewayError {

        public GenericGatewayErrorException(String message) {
            super(message);
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GENERIC_GATEWAY_ERROR);
        }
    }

    public static class GatewayConnectionTimeoutErrorException extends Exception implements ToGatewayError {

        public GatewayConnectionTimeoutErrorException(String message) {
            super(message);
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR);
        }
    }

    public static class GatewayConnectionErrorException extends Exception implements ToGatewayError {

        public GatewayConnectionErrorException(String message) {
            super(message);
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GATEWAY_CONNECTION_ERROR);
        }
    }
}
