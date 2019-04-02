package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.GatewayError;

import javax.ws.rs.core.Response.Status.Family;

import static javax.ws.rs.core.Response.Status.Family.*;

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
        private final int status;

        public GatewayErrorException(String message, String responseFromGateway, int status) {
            super(message);
            this.responseFromGateway = responseFromGateway;
            this.status = status;
        }

        public GatewayErrorException(String message) {
            super(message);
            this.responseFromGateway = "null";
            this.status = 0;
        }

        public int getStatus() {
            return status;
        }

        public String getResponseFromGateway() {
            return responseFromGateway;
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GATEWAY_ERROR);
        }
        
        public Family getFamily() {
            if (Family.familyOf(status) == CLIENT_ERROR) return CLIENT_ERROR;
            if (Family.familyOf(status) == SERVER_ERROR) return SERVER_ERROR;
            return OTHER;
        }
    }
}
