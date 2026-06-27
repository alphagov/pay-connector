package uk.gov.pay.connector.gateway.adyen.webhook;

public enum AdyenPaymentEvent {
    CAPTURE,
    AUTHORISATION,
    CANCELLATION,
    CAPTURE_FAILED,
    EXPIRE,
    REFUND,
    REFUND_FAILED,
    REFUNDED_REVERSED,
    NOTIFICATION_OF_CHARGEBACK,
    CHARGEBACK,
    CHARGEBACK_REVERSED,
    INFORMATION_SUPPLIED,
    PREARBITRATION_WON,
    PREARBITRATION_LOST;

    public static boolean contains(String eventName) {
        for (AdyenPaymentEvent e : AdyenPaymentEvent.values()) {
            if (e.name().equals(eventName)) {
                return true;
            }
        }
        return false;
    }
}
