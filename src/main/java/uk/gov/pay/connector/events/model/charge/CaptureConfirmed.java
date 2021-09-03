package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureConfirmedEventDetails;

import java.time.ZonedDateTime;

/**
 *  Confirmed by notification from payment gateway
 **/
public class CaptureConfirmed extends PaymentEvent {
    public CaptureConfirmed(String serviceId, boolean live, String resourceExternalId, CaptureConfirmedEventDetails captureConfirmedEventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, captureConfirmedEventDetails, timestamp);
    }

    public static CaptureConfirmed from(ChargeEventEntity chargeEvent) {
        ChargeEntity charge = chargeEvent.getChargeEntity();

        return new CaptureConfirmed(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getExternalId(),
                CaptureConfirmedEventDetails.from(chargeEvent),
                chargeEvent.getUpdated()
        );
    }

}
