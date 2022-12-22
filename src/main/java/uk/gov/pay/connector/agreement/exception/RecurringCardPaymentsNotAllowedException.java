package uk.gov.pay.connector.agreement.exception;

public class RecurringCardPaymentsNotAllowedException extends RuntimeException{
    public RecurringCardPaymentsNotAllowedException(Long gatewayAccountId) {
        super("Attempt to create an agreement for gateway account " + gatewayAccountId +
                ", which does not have recurring card payments enabled");
    }
}

