package uk.gov.pay.connector.gateway.stripe;

/**
 * This class represents 4xx exceptions
 */
public class GatewayClientException extends Exception {
    private final transient StripeGatewayClientResponse response;

    public GatewayClientException(String message, StripeGatewayClientResponse response) {
        super(message);
        this.response = response;
    }

    public StripeGatewayClientResponse getResponse() {
        return response;
    }
}
