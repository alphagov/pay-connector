package uk.gov.pay.connector.gatewayaccountcredentials.exception;

public class NoCredentialsInUsableStateException extends RuntimeException {
    public NoCredentialsInUsableStateException() {
        super("Payment provider details are not configured on this account");
    }
}
