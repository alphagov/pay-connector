package uk.gov.pay.connector.events;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

/**
 * @TODO(sfount) should this be moved to getTitle/ getDescription methods?
 * Payment has been processed by the `ChargeExpiryProcess`, this can result in the payment
 * succeeding or failing to expire.
 */
public class PaymentExpiredEvent extends PaymentEvent {


    @Override
    public String getResourceExternalId() {
        return null;
    }

    @Override
    public String getEventType() {
        return null;
    }

    @Override
    public EventDetails getEventDetails() {
        return null;
    }

    @Override
    public ZonedDateTime getTimestamp() {
        return null;
    }
}
