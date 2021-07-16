package uk.gov.pay.connector.gatewayaccountcredentials.exception;

public class GatewayAccountCredentialsNotConfiguredException extends RuntimeException {
    public GatewayAccountCredentialsNotConfiguredException() {
        super("Payment provider details are not configured on this account");
    }
}
