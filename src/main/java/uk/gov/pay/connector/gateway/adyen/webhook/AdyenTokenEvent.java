package uk.gov.pay.connector.gateway.adyen.webhook;

public enum AdyenTokenEvent {
    RECURRING_TOKEN_CREATED("recurring.token.created"),
    RECURRING_TOKEN_DISABLED("recurring.token.disabled");

    private final String eventName;

    AdyenTokenEvent(String eventName) {
        this.eventName = eventName;
    }

    public static boolean contains(String eventName) {
        for (AdyenTokenEvent event : AdyenTokenEvent.values()) {
            if (event.eventName.equals(eventName)) {
                return true;
            }
        }
        return false;
    }
}
