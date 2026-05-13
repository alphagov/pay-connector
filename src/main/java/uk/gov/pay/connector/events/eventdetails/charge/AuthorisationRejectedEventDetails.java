package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

public abstract class AuthorisationRejectedEventDetails extends EventDetails {
    protected final String gatewayRejectionReason;

    protected AuthorisationRejectedEventDetails(String gatewayRejectionReason) {
        this.gatewayRejectionReason = gatewayRejectionReason;
    }

    public String getGatewayRejectionReason() {
        return gatewayRejectionReason;
    }
}
