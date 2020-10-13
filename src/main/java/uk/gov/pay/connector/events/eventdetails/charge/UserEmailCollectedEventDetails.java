package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class UserEmailCollectedEventDetails extends EventDetails {
    private final String email;

    public UserEmailCollectedEventDetails(String email) {
        this.email = email;
    }

    public static UserEmailCollectedEventDetails from(ChargeEntity chargeEntity) {
        return new UserEmailCollectedEventDetails(chargeEntity.getEmail());
    }

    public String getEmail() {
        return email;
    }
}
