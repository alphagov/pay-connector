package uk.gov.pay.connector.events.eventdetails.refund;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class RefundEventWithReferenceDetails extends EventDetails {

    private String reference;
    private String gatewayTransactionId;

    public RefundEventWithReferenceDetails(String reference, String gatewayTransactionId) {
        this.reference = reference;
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public String getReference() {
        return reference;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }
}
