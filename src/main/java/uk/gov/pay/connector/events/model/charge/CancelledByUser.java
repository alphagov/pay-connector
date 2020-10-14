package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.CancelledByUserEventDetails;

import java.time.ZonedDateTime;

public class CancelledByUser extends PaymentEvent {
    public CancelledByUser(String resourceExternalId, CancelledByUserEventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    public static CancelledByUser from(ChargeEventEntity chargeEvent) {
        ChargeEntity charge = chargeEvent.getChargeEntity();
        return new CancelledByUser(charge.getExternalId(), CancelledByUserEventDetails.from(charge), chargeEvent.getUpdated());
    }
}
