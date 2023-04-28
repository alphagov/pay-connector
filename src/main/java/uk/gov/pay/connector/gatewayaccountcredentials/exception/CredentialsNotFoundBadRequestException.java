package uk.gov.pay.connector.gatewayaccountcredentials.exception;

public class CredentialsNotFoundBadRequestException extends RuntimeException {
    public CredentialsNotFoundBadRequestException(String message) {
        super(message);
    }
}
