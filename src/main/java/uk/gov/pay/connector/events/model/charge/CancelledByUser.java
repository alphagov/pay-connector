package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.CancelledByUserEventDetails;

import java.time.ZonedDateTime;

public class CancelledByUser extends PaymentEvent {
    public CancelledByUser(String serviceId, boolean live, String resourceExternalId, CancelledByUserEventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static CancelledByUser from(ChargeEventEntity chargeEvent) {
        ChargeEntity charge = chargeEvent.getChargeEntity();
        return new CancelledByUser(charge.getServiceId(), charge.getGatewayAccount().isLive(), charge.getExternalId(), CancelledByUserEventDetails.from(charge), chargeEvent.getUpdated());
    }
}
