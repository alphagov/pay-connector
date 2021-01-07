package uk.gov.pay.connector.charge.exception;

public class TelephonePaymentNotificationsNotAllowedException extends RuntimeException {
    public TelephonePaymentNotificationsNotAllowedException(Long gatewayAccountId) {
        super(String.format("Attempt to create telephone payment record for gateway account %s which does not have " +
                "telephone payment notifications enabled", gatewayAccountId));
    }
}
