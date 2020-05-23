package uk.gov.pay.connector.events.eventdetails.refund;

public class RefundCreatedByServiceEventDetails extends RefundCreatedEventDetails {

    public RefundCreatedByServiceEventDetails(Long refundAmount, String gatewayAccountId) {
        super(refundAmount, gatewayAccountId);
    }

    @Override
    public String toString() {
        return "RefundCreatedByServiceEventDetails{} " + super.toString();
    }
}
