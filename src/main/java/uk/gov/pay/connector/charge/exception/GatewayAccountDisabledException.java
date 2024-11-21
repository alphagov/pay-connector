package uk.gov.pay.connector.charge.exception;

public final class GatewayAccountDisabledException extends RuntimeException implements ErrorListMapper.Error {

    public GatewayAccountDisabledException(String message) {
        super(message);
    }
    
}
