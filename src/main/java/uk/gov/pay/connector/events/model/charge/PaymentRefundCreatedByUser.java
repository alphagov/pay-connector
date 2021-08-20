package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

public class PaymentRefundCreatedByUser extends PaymentEvent {
    public PaymentRefundCreatedByUser(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }
}
