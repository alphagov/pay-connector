package uk.gov.pay.connector.events;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

/**
 * @TODO(sfount) should this be moved to getTitle/ getDescription methods?
 * Payment has moved to a success state where it has started
 *
 * @TODO(sfount) proposed: not salient - replace with `PaymentInternalStateUpdatedEvent`
 *               (compliment of `PaymentDataUpdatedEvent`)
 */
public class PaymentStartedEvent extends PaymentEvent {


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
