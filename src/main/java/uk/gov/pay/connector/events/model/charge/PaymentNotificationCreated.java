package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentNotificationCreatedEventDetails;

import java.time.ZonedDateTime;

public class PaymentNotificationCreated extends PaymentEvent {
    public PaymentNotificationCreated(String resourceExternalId, PaymentNotificationCreatedEventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentNotificationCreated from(ChargeEventEntity chargeEvent) {
        ChargeEntity charge = chargeEvent.getChargeEntity();
        return new  PaymentNotificationCreated(charge.getExternalId(),
                PaymentNotificationCreatedEventDetails.from(charge),
                chargeEvent.getUpdated());
    }
}
