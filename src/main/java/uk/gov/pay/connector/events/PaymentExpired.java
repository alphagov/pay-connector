package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

/**
 * Payment has been processed by the `ChargeExpiryProcess`, this can result in the payment
 * succeeding or failing to expire.
 */
public class PaymentExpired extends PaymentEventWithoutDetails {
    public PaymentExpired(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
