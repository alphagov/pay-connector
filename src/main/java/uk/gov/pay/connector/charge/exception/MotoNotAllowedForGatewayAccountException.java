package uk.gov.pay.connector.charge.exception;

public class MotoNotAllowedForGatewayAccountException extends RuntimeException {

    public MotoNotAllowedForGatewayAccountException(long gatewayAccountId) {
        super("Attempt to create a moto charge for gateway account " + gatewayAccountId + ", which does not have moto charges enabled");
    }

}
