package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureConfirmedEventDetails;

import java.time.Instant;

/**
 * Confirmed by notification from payment gateway
 **/
public class CaptureConfirmed extends PaymentEvent {
    public CaptureConfirmed(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId,
                            CaptureConfirmedEventDetails captureConfirmedEventDetails, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, captureConfirmedEventDetails, timestamp);
    }

    public static CaptureConfirmed from(ChargeEventEntity chargeEvent) {
        ChargeEntity charge = chargeEvent.getChargeEntity();

        return new CaptureConfirmed(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                charge.getExternalId(),
                CaptureConfirmedEventDetails.from(chargeEvent),
                chargeEvent.getUpdated().toInstant()
        );
    }

}
