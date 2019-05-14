package uk.gov.pay.connector.charge.exception;

public class ZeroAmountNotAllowedForGatewayAccountException extends RuntimeException {

    public ZeroAmountNotAllowedForGatewayAccountException(long gatewayAccountId) {
        super("Attempt to create a zero amount charge for gateway account " + gatewayAccountId + ", which does not have zero amount charges enabled");
    }

}
