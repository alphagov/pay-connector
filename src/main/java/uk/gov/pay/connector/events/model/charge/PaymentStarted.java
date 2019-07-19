package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

/**
 * Payment has moved to a success state where it has started
 *
 */
public class PaymentStarted extends PaymentEventWithoutDetails {
    public PaymentStarted(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
