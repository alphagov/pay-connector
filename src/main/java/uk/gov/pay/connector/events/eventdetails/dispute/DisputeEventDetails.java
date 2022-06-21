package uk.gov.pay.connector.events.eventdetails.dispute;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

abstract class DisputeEventDetails extends EventDetails {
    protected final String gatewayAccountId;

    public DisputeEventDetails(String gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
    }

    public String getGatewayAccountId() {
        return gatewayAccountId;
    }
}
