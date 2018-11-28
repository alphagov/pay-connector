package uk.gov.pay.connector.gateway.stripe;

public class StripeParseException extends Exception {
    public StripeParseException(String message) {
        super(message);
    }
}
