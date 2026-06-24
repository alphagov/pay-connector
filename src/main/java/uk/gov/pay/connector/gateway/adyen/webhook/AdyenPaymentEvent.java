package uk.gov.pay.connector.gateway.adyen.webhook;

public enum AdyenPaymentEvent {
    CAPTURE,
    AUTHORISATION,
    CANCELLATION,
    CAPTURE_FAILED,
    EXPIRE,
    REFUND,
    REFUND_FAILED,
    REFUNDED_REVERSED;

    public static boolean contains(String eventName) {
        for (AdyenPaymentEvent e : AdyenPaymentEvent.values()) {
            if (e.name().equals(eventName)) {
                return true;
            }
        }
        return false;
    }
}
