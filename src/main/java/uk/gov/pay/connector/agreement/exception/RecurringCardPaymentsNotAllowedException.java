package uk.gov.pay.connector.agreement.exception;

public class RecurringCardPaymentsNotAllowedException extends RuntimeException{
    public RecurringCardPaymentsNotAllowedException(String message) {
        super(message);
    }
}

