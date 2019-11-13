package uk.gov.pay.connector.gateway.stripe;

import java.util.Arrays;

public enum StripeNotificationType {

    ACCOUNT_UPDATED("account.updated"),
    SOURCE_CANCELED("source.canceled"),
    SOURCE_CHARGEABLE("source.chargeable"),
    SOURCE_FAILED("source.failed"),
    PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED("payment_intent.amount_capturable_updated"),
    PAYMENT_INTENT_PAYMENT_FAILED("payment_intent.payment_failed"),
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
