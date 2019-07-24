package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.charge.RefundAvailabilityUpdatedEventDetails;

import java.time.ZonedDateTime;

public class RefundAvailabilityUpdated extends PaymentEvent {

    public RefundAvailabilityUpdated(String resourceExternalId, RefundAvailabilityUpdatedEventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }
}
