package uk.gov.pay.connector.charge.exception;

public class MotoPaymentNotAllowedForGatewayAccountException extends RuntimeException {
    public MotoPaymentNotAllowedForGatewayAccountException(Long gatewayAccountId) {
        super("Attempt to create a MOTO payment for gateway account " + gatewayAccountId + ", which does not have MOTO payments enabled");
    }
}
