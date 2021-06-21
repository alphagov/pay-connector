package uk.gov.pay.connector.gatewayaccountcredentials.exception;

import static java.lang.String.format;

public class NoCredentialsExistForProviderException extends RuntimeException {
    public NoCredentialsExistForProviderException(String paymentProvider) {
        super(format("Account does not support payment provider [%s]", paymentProvider));
    }
}
