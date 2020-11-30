package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.charge.UserEmailCollectedEventDetails;

import java.time.ZonedDateTime;

public class UserEmailCollected extends PaymentEvent {

    public UserEmailCollected(String resourceExternalId,
                              UserEmailCollectedEventDetails eventDetails,
                              ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    public static UserEmailCollected from(ChargeEntity charge, ZonedDateTime eventDate) {
        return new UserEmailCollected(
                charge.getExternalId(),
                UserEmailCollectedEventDetails.from(charge),
                eventDate);
    }
}
