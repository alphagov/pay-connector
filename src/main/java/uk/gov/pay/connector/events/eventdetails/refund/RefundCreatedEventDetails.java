package uk.gov.pay.connector.events.eventdetails.refund;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

abstract class RefundCreatedEventDetails extends EventDetails {

    private Long amount;
    private String gatewayAccountId;

    public RefundCreatedEventDetails(Long amount, String gatewayAccountId) {
        this.amount = amount;
        this.gatewayAccountId = gatewayAccountId;
    }

    public Long getAmount() {
        return amount;
    }

    public String getGatewayAccountId() {
        return gatewayAccountId;
    }
}
