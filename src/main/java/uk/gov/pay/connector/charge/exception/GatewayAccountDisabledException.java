package uk.gov.pay.connector.charge.exception;

public class GatewayAccountDisabledException extends RuntimeException {

    public GatewayAccountDisabledException(long gatewayAccountId) {
        super("Attempt to create charge for gateway account " + gatewayAccountId + ", which is disabled");
    }
    
}
