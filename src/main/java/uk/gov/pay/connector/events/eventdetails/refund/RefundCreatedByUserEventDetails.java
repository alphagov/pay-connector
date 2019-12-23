package uk.gov.pay.connector.events.eventdetails.refund;

public class RefundCreatedByUserEventDetails extends RefundCreatedEventDetails {

    private String refundedBy;
    private String userEmail;

    public RefundCreatedByUserEventDetails(Long refundAmount, String refundedBy, String gatewayAccountId,
                                           String userEmail) {
        super(refundAmount, gatewayAccountId);
        this.refundedBy = refundedBy;
        this.userEmail = userEmail;
    }

    public String getRefundedBy() {
        return refundedBy;
    }

    public String getUserEmail() { return userEmail; }
}
