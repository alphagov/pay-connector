package uk.gov.pay.connector.events;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.PaymentCreatedEventDetails;

import java.time.ZonedDateTime;

public class PaymentCreatedEvent extends PaymentEvent {

    public PaymentCreatedEvent(String resourceExternalId, PaymentCreatedEventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentCreatedEvent from(ChargeEntity charge) {
        return new PaymentCreatedEvent(
                charge.getExternalId(), 
                PaymentCreatedEventDetails.from(charge),
                charge.getCreatedDate());
    }
}
