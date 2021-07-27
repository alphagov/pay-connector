package uk.gov.pay.connector.gateway.stripe;

import uk.gov.pay.connector.events.model.payout.PayoutCreated;
import uk.gov.pay.connector.events.model.payout.PayoutEvent;
import uk.gov.pay.connector.events.model.payout.PayoutFailed;
import uk.gov.pay.connector.events.model.payout.PayoutPaid;
import uk.gov.pay.connector.events.model.payout.PayoutUpdated;

import java.util.Arrays;
import java.util.Optional;

public enum StripeNotificationType {

    ACCOUNT_UPDATED("account.updated"),
    SOURCE_CANCELED("source.canceled"),
    SOURCE_CHARGEABLE("source.chargeable"),
    SOURCE_FAILED("source.failed"),
    PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED("payment_intent.amount_capturable_updated"),
    PAYMENT_INTENT_PAYMENT_FAILED("payment_intent.payment_failed"),
    PAYOUT_CREATED("payout.created", PayoutCreated.class),
    PAYOUT_PAID("payout.paid", PayoutPaid.class),
    PAYOUT_UPDATED("payout.updated", PayoutUpdated.class),
    PAYOUT_FAILED("payout.failed", PayoutFailed.class),
    REFUND_UPDATED("charge.refund.updated"),
    UNKNOWN("");

    private final String type;
    private final Class<? extends PayoutEvent> eventClass;

    StripeNotificationType(final String type) {
        this(type, null);
    }

    <T extends PayoutEvent> StripeNotificationType(final String type, Class<T> eventClass) {
        this.type = type;
        this.eventClass = eventClass;
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

    public Optional<Class<? extends PayoutEvent>> getEventClass() {
        return Optional.ofNullable(eventClass);
    }

    @Override
    public String toString() {
        return this.type;
    }
}
