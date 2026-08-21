package uk.gov.pay.connector.gateway.adyen.webhook;

public enum AdyenTokenEvent {

    RECURRING_TOKEN_CREATED("recurring.token.created"),
    RECURRING_TOKEN_DISABLED("recurring.token.disabled");

    private final String name;

    AdyenTokenEvent(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    public static boolean contains(String eventName) {
        for (AdyenTokenEvent e : AdyenTokenEvent.values()) {
            if (e.getName().equals(eventName)) {
                return true;
            }
        }
        return false;
    }
}

