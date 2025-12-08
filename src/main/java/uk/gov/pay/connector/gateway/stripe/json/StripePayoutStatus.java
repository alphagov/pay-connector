package uk.gov.pay.connector.gateway.stripe.json;

import org.apache.commons.lang3.Strings;
import uk.gov.pay.connector.events.model.payout.PayoutEvent;
import uk.gov.pay.connector.events.model.payout.PayoutFailed;
import uk.gov.pay.connector.events.model.payout.PayoutPaid;

import java.util.Optional;

import static java.lang.String.format;

public enum StripePayoutStatus {
    PENDING("pending", false),
    IN_TRANSIT("in_transit", false),
    CANCELLED("canceled", true),
    PAID("paid", true, PayoutPaid.class),
    FAILED("failed", true, PayoutFailed.class);

    private final String status;
    private final boolean isTerminal;
    private final Class<? extends PayoutEvent> eventClass;

    StripePayoutStatus(final String status, boolean isTerminal) {
        this(status, isTerminal, null);
    }

    <T extends PayoutEvent> StripePayoutStatus(final String status, boolean isTerminal, Class<T> eventClass) {
        this.status = status;
        this.eventClass = eventClass;
        this.isTerminal = isTerminal;
    }

    public static StripePayoutStatus fromString(String status) {
        for (StripePayoutStatus stat : values()) {
            if (Strings.CS.equals(stat.getStatus(), status)) {
                return stat;
            }
        }
        throw new IllegalArgumentException(format("Stripe payout status not recognized: [%s]", status));
    }

    public Optional<Class<? extends PayoutEvent>> getEventClass() {
        return Optional.ofNullable(eventClass);
    }

    public boolean isTerminal() {
        return isTerminal;
    }

    public String getStatus() {
        return status;
    }
}
