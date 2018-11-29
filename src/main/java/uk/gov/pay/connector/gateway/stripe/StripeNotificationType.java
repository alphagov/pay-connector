package uk.gov.pay.connector.gateway.stripe;

import java.util.Arrays;

public enum StripeNotificationType {

    SOURCE_CANCELED("source.canceled"),
    SOURCE_CHARGEABLE("source.chargeable"),
    SOURCE_FAILED("source.failed"),
    UNKNOWN("");

    private final String type;

    StripeNotificationType(final String type) {
        this.type = type;
    }

    public static StripeNotificationType byType(String type) {
        return Arrays.stream(StripeNotificationType.values())
                .filter(c -> c.getType().equals(type))
                .findFirst()
                .orElse(UNKNOWN);
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return this.type;
    }
}
