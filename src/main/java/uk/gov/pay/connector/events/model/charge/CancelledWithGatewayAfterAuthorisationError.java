package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.CancelledWithGatewayAfterAuthorisationErrorEventDetails;

import java.time.Instant;

public class CancelledWithGatewayAfterAuthorisationError extends PaymentEvent {
    public CancelledWithGatewayAfterAuthorisationError(String serviceId, boolean live, String resourceExternalId,
                                                       CancelledWithGatewayAfterAuthorisationErrorEventDetails eventDetails, 
                                                       Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }
    
    public static CancelledWithGatewayAfterAuthorisationError from(ChargeEventEntity chargeEvent) {
        ChargeEntity charge = chargeEvent.getChargeEntity();
        
        return new CancelledWithGatewayAfterAuthorisationError(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getExternalId(),
                CancelledWithGatewayAfterAuthorisationErrorEventDetails.from(charge),
                chargeEvent.getUpdated().toInstant()
        );
    }
}
