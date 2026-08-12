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
    RECURRING_TOKEN_CREATED("recurring.token.created"),
    RECURRING_TOKEN_DISABLED("recurring.token.disabled");

    private final String name;

    AdyenPaymentEvent(String name) {
        this.name = name;
    }

    AdyenPaymentEvent() {
        this.name = name();
    }

    public String getName() {
        return name;
    }

    public static boolean contains(String eventName) {
        for (AdyenPaymentEvent e : AdyenPaymentEvent.values()) {
            if (e.name().equals(eventName)) {
                return true;
            }
        }
        return false;
    }
}
