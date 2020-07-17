package uk.gov.pay.connector.events.eventdetails.refund;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class RefundEventWithGatewayTransactionIdDetails extends EventDetails {

    private String gatewayTransactionId;

    public RefundEventWithGatewayTransactionIdDetails(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }
}
