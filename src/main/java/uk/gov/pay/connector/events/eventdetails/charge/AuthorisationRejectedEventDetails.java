package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class AuthorisationRejectedEventDetails extends EventDetails {
    private final boolean canRetry;

    private AuthorisationRejectedEventDetails(boolean canRetry) {
        this.canRetry = canRetry;
    }

    public static AuthorisationRejectedEventDetails from(ChargeEntity charge) {
        return new AuthorisationRejectedEventDetails(charge.getCanRetry());
    }

    public boolean getCanRetry() {
        return canRetry;
    }
}
