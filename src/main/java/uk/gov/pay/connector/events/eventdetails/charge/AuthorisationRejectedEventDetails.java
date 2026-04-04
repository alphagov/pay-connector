package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class AuthorisationRejectedEventDetails extends EventDetails {
    private final Boolean canRetry;
    private final String gatewayRejectionReason;

    private AuthorisationRejectedEventDetails(Boolean canRetry, String gatewayRejectionReason) {
        this.canRetry = canRetry;
        this.gatewayRejectionReason = gatewayRejectionReason;
    }

    public static AuthorisationRejectedEventDetails from(ChargeEntity charge) {
        return new AuthorisationRejectedEventDetails(charge.getCanRetry(), charge.getGatewayRejectionReason());
    }

    public Boolean getCanRetry() {
        return canRetry;
    }
    
    public String getGatewayRejectionReason() {
        return gatewayRejectionReason;
    }
}
