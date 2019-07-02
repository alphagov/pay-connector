package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class PaymentEventWithoutDetails extends PaymentEvent {
    public PaymentEventWithoutDetails(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, new EmptyEventDetails(), timestamp);
    }
}
