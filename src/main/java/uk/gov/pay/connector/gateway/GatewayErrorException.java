package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.GatewayError;

public abstract class GatewayErrorException extends Exception {
    
    private GatewayErrorException() {}

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

        private final Integer statusCode;
        private final String responseEntity;

        public GatewayConnectionErrorException(String message, Integer statusCode, String responseEntity) {
            super(message);
            this.statusCode = statusCode;
            this.responseEntity = responseEntity;
        }

        public Integer getStatusCode() {
            return statusCode;
        }

        public String getResponseEntity() {
            return responseEntity;
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GATEWAY_CONNECTION_ERROR);
        }
    }
}
