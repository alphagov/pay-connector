package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.charge.RefundAvailabilityUpdatedEventDetails;

import java.time.ZonedDateTime;

public class RefundAvailabilityUpdated extends PaymentEvent {

    public RefundAvailabilityUpdated(String serviceId, boolean live, String resourceExternalId, RefundAvailabilityUpdatedEventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }
}
