package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.charge.PaymentNotificationCreatedEventDetails;

import java.time.ZonedDateTime;

public class PaymentNotificationCreated extends PaymentEvent {
    public PaymentNotificationCreated(String resourceExternalId, PaymentNotificationCreatedEventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }
}
