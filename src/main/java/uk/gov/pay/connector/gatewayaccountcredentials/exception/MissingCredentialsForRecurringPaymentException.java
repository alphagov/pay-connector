package uk.gov.pay.connector.gatewayaccountcredentials.exception;

public class MissingCredentialsForRecurringPaymentException extends RuntimeException {
    public MissingCredentialsForRecurringPaymentException() {
        super("Credentials are missing for merchant initiated recurring payment");
    }
}
