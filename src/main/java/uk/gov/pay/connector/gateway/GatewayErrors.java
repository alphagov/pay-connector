package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.GatewayError;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;

public class GatewayErrors {

    public interface ToGatewayError {
        GatewayError toGatewayError();
    }
    
    public interface ToChargeStatus {
        ChargeStatus chargeStatus();
    }

    public static class GenericGatewayErrorException extends Exception implements ToGatewayError, ToChargeStatus {

        public GenericGatewayErrorException(String message) {
            super(message);
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GENERIC_GATEWAY_ERROR);
        }

        public ChargeStatus chargeStatus() {
            return AUTHORISATION_ERROR;
        }
    }

    public static class GatewayConnectionTimeoutErrorException extends Exception implements ToGatewayError, ToChargeStatus {

        public GatewayConnectionTimeoutErrorException(String message) {
            super(message);
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR);
        }

        public ChargeStatus chargeStatus() {
            return AUTHORISATION_TIMEOUT;
        }
    }

    public static class GatewayConnectionErrorException extends Exception implements ToGatewayError, ToChargeStatus {

        public GatewayConnectionErrorException(String message) {
            super(message);
        }

        public GatewayError toGatewayError() {
            return new GatewayError(getMessage(), ErrorType.GATEWAY_CONNECTION_ERROR);
        }

        public ChargeStatus chargeStatus() {
            return AUTHORISATION_UNEXPECTED_ERROR;
        }
    }
}
