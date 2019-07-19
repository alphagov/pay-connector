package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.EmptyEventDetails;

import java.time.ZonedDateTime;

public class PaymentEventWithoutDetails extends PaymentEvent {
    public PaymentEventWithoutDetails(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, new EmptyEventDetails(), timestamp);
    }
}
