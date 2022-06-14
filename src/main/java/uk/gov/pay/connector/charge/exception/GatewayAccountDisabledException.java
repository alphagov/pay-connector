package uk.gov.pay.connector.charge.exception;

public class GatewayAccountDisabledException extends RuntimeException {

    public GatewayAccountDisabledException(String message) {
        super(message);
    }
    
}
