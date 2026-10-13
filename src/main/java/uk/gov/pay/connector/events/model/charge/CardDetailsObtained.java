package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

import java.time.Instant;

public class CardDetailsObtained extends PaymentEvent {
    public CardDetailsObtained(
            String serviceId, 
            boolean live, 
            Long gatewayAccountId, 
            String resourceExternalId, 
            CardDetailsObtainedEventDetails eventDetails, 
            Instant timestamp
    ) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }
    
    public static CardDetailsObtained from(ChargeEntity charge, Instant eventDate) {
        return new CardDetailsObtained(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                charge.getExternalId(),
                CardDetailsObtainedEventDetails.from(charge.getCardDetails().getExpiryDate()),
                eventDate       
        );
    }
}
