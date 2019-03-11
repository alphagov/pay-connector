package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.GatewayError;

public abstract class GatewayErrorException extends Exception {

    private GatewayErrorException(String message) {
        super(message);
    }

    public abstract GatewayError toGatewayError();

    public static class GenericGatewayErrorException extends GatewayErrorException {

        public GenericGatewayErrorException(String message) {
            super(message);
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GENERIC_GATEWAY_ERROR);
        }
    }

    public static class GatewayConnectionTimeoutErrorException extends GatewayErrorException {

        public GatewayConnectionTimeoutErrorException(String message) {
            super(message);
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR);
        }
    }

    public static class GatewayConnectionErrorException extends GatewayErrorException {

        private final String responseFromGateway;

        public GatewayConnectionErrorException(String message, String responseFromGateway) {
            super(message);
            this.responseFromGateway = responseFromGateway;
        }

        public GatewayConnectionErrorException(String message) {
            super(message);
            this.responseFromGateway = "null";
        }

        public String getResponseFromGateway() {
            return responseFromGateway;
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GATEWAY_CONNECTION_ERROR);
        }
    }
}
