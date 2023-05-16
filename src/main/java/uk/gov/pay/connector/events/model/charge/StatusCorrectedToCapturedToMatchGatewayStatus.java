package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureConfirmedEventDetails;

import java.time.Instant;

public class StatusCorrectedToCapturedToMatchGatewayStatus extends PaymentEvent {
    public StatusCorrectedToCapturedToMatchGatewayStatus(String serviceId, boolean live, Long gatewayAccountInternalId, String resourceExternalId,
                                                         CaptureConfirmedEventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, gatewayAccountInternalId, resourceExternalId, eventDetails, timestamp);
    }
    
    public static StatusCorrectedToCapturedToMatchGatewayStatus from(ChargeEventEntity chargeEvent) {
        ChargeEntity charge = chargeEvent.getChargeEntity();
        
        return new StatusCorrectedToCapturedToMatchGatewayStatus(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                charge.getExternalId(),
                CaptureConfirmedEventDetails.from(chargeEvent),
                chargeEvent.getUpdated().toInstant()
        );
    }
}
