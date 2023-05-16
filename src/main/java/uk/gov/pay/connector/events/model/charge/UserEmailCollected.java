package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.charge.UserEmailCollectedEventDetails;

import java.time.Instant;

public class UserEmailCollected extends PaymentEvent {

    public UserEmailCollected(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId,
                              UserEmailCollectedEventDetails eventDetails,
                              Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }

    public static UserEmailCollected from(ChargeEntity charge, Instant eventDate) {
        return new UserEmailCollected(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                charge.getExternalId(),
                UserEmailCollectedEventDetails.from(charge),
                eventDate);
    }
}
