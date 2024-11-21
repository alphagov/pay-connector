package uk.gov.pay.connector.charge.exception;

public final class MotoPaymentNotAllowedForGatewayAccountException extends RuntimeException implements ErrorListMapper.Error {
    public MotoPaymentNotAllowedForGatewayAccountException(Long gatewayAccountId) {
        super("Attempt to create a MOTO payment for gateway account " + gatewayAccountId + ", which does not have MOTO payments enabled");
    }
}
