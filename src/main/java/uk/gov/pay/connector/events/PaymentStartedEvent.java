package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

/**
 * @TODO(sfount) should this be moved to getTitle/ getDescription methods?
 * Payment has moved to a success state where it has started
 *
 * @TODO(sfount) proposed: not salient - replace with `PaymentInternalStateUpdatedEvent`
 *               (compliment of `PaymentDataUpdatedEvent`)
 */
public class PaymentStartedEvent extends PaymentEventWithoutDetails {
    public PaymentStartedEvent(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
