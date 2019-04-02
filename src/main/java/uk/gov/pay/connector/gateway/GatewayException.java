package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.GatewayError;

import java.util.Optional;

public abstract class GatewayException extends Exception {

    private GatewayException(String message) {
        super(message);
    }

    public abstract GatewayError toGatewayError();

    public static class GenericGatewayException extends GatewayException {

        public GenericGatewayException(String message) {
            super(message);
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GENERIC_GATEWAY_ERROR);
        }
    }

    public static class GatewayConnectionTimeoutException extends GatewayException {

        public GatewayConnectionTimeoutException(String message) {
            super(message);
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR);
        }
    }

    public static class GatewayErrorException extends GatewayException {

        private final String responseFromGateway;
        private final Integer status;

        public GatewayErrorException(String message, String responseFromGateway, int status) {
            super(message);
            this.responseFromGateway = responseFromGateway;
            this.status = status;
        }

        public GatewayErrorException(String message) {
            super(message);
            this.responseFromGateway = "null";
            this.status = null;
        }

        public Optional<Integer> getStatus() {
            return Optional.ofNullable(status);
        }

        public String getResponseFromGateway() {
            return responseFromGateway;
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GATEWAY_CONNECTION_ERROR);
        }
    }
}
