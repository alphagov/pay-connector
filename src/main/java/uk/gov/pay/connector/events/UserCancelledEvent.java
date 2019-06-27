package uk.gov.pay.connector.events;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

/**
 * Payment has been cancelled by a user
 *     @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/cancel")
 *
 */
public class UserCancelledEvent extends PaymentEvent {

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
