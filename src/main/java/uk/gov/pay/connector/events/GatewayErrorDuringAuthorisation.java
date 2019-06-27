package uk.gov.pay.connector.events;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

/**
 * @TODO(sfount) should this be moved to getTitle/ getDescription methods?
 * Payment has moved to an error state - this is usually because of a gateway error
 *
 * @TODO(sfount) salient - move to error state
 * @TODO(sfount) abstract to `PaymentFailed` ?
 */
public class GatewayErrorDuringAuthorisation extends PaymentEvent {


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
