package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.charge.UserEmailCollectedEventDetails;

import java.time.ZonedDateTime;

public class UserEmailCollected extends PaymentEvent {

    public UserEmailCollected(String serviceId, boolean isLive, String resourceExternalId,
                              UserEmailCollectedEventDetails eventDetails,
                              ZonedDateTime timestamp) {
        super(serviceId, isLive, resourceExternalId, eventDetails, timestamp);
    }

    public static UserEmailCollected from(ChargeEntity charge, ZonedDateTime eventDate) {
        return new UserEmailCollected(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getExternalId(),
                UserEmailCollectedEventDetails.from(charge),
                eventDate);
    }
}
