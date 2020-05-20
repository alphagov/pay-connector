package uk.gov.pay.connector.events.eventdetails.payout;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class PayoutEventWithGatewayStatusDetails extends EventDetails {

    private String gatewayStatus;

    public PayoutEventWithGatewayStatusDetails(String gatewayStatus) {
        this.gatewayStatus = gatewayStatus;
    }

    public String getGatewayStatus() {
        return gatewayStatus;
    }
}
