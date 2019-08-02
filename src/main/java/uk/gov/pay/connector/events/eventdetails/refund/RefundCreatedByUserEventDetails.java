package uk.gov.pay.connector.events.eventdetails.refund;

public class RefundCreatedByUserEventDetails extends RefundCreatedEventDetails {

    private String refundedBy;

    public RefundCreatedByUserEventDetails(Long refundAmount, String refundedBy, String gatewayAccountId) {
        super(refundAmount, gatewayAccountId);
        this.refundedBy = refundedBy;
    }

    public String getRefundedBy() {
        return refundedBy;
    }
}
