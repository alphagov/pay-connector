package uk.gov.pay.connector.gateway.adyen.webhook.model;

import java.util.Arrays;
import java.util.Optional;

import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookType.PAYMENTS;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookType.TOKENS;

public enum AdyenWebhookEvent {
    // payment events
    AUTHORISATION("AUTHORISATION", PAYMENTS, false),
    CANCELLATION("CANCELLATION", PAYMENTS, false),
    CAPTURE("CAPTURE", PAYMENTS, false),
    CAPTURE_FAILED("CAPTURE_FAILED", PAYMENTS, false),
    REFUND("REFUND", PAYMENTS, false),
    REFUND_FAILED("REFUND_FAILED", PAYMENTS, false),
    REFUNDED_REVERSED("REFUNDED_REVERSED", PAYMENTS, false),
    EXPIRE("EXPIRE", PAYMENTS, true),
    ORDER_OPENED("ORDER_OPENED", PAYMENTS, true),
    ORDER_CLOSED("ORDER_CLOSED", PAYMENTS, true),

    // tokens
    RECURRING_TOKEN_CREATED("recurring.token.created", TOKENS, false),
    RECURRING_TOKEN_DISABLED("recurring.token.disabled", TOKENS, false),
    RECURRING_TOKEN_UPDATED("recurring.token.updated", TOKENS, true),
    RECURRING_TOKEN_ALREADY_EXISTING("recurring.token.alreadyExisting", TOKENS, true);

    private final AdyenWebhookType webhookType;
    private final String eventCodeOrType;
    private final boolean isIgnored;

    AdyenWebhookEvent(String eventCodeOrType, AdyenWebhookType webhookType, boolean isIgnored) {
        this.webhookType = webhookType;
        this.eventCodeOrType = eventCodeOrType;
        this.isIgnored = isIgnored;
    }

    public AdyenWebhookType getWebhookType() {
        return webhookType;
    }

    public String getEventCodeOrType() {
        return eventCodeOrType;
    }

    public boolean isIgnored() {
        return isIgnored;
    }

    public static Optional<AdyenWebhookEvent> fromEventCodeOrType(String eventCodeOrType) {
        return Arrays.stream(values())
                .filter(event -> event.eventCodeOrType.equals(eventCodeOrType))
                .findFirst();
    }
}
