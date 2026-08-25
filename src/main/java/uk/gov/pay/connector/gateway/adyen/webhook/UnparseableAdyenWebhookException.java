package uk.gov.pay.connector.gateway.adyen.webhook;

public class UnparseableAdyenWebhookException extends RuntimeException {
    public UnparseableAdyenWebhookException(String message) {
        super(message);
    }
}
