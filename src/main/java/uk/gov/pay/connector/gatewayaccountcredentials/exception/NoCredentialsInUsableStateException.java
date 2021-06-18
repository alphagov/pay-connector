package uk.gov.pay.connector.gatewayaccountcredentials.exception;

import static java.lang.String.format;

public class NoCredentialsInUsableStateException extends RuntimeException {
    public NoCredentialsInUsableStateException(String paymentProvider) {
        super(format("Account does not have credentials in a usable state for payment provider [%s]", paymentProvider));
    }
}
