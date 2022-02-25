package uk.gov.pay.connector.events.eventdetails.dispute;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class DisputeCreatedEventDetails extends EventDetails {
    private Long fee;
    private Long evidenceDueDate;
    private String gatewayAccountId;
    private Long amount;
    private Long netAmount;
    private String reason;

    public DisputeCreatedEventDetails(Long fee, Long evidenceDueDate, String gatewayAccountId, Long amount, Long netAmount, String reason) {
        this.fee = fee;
        this.evidenceDueDate = evidenceDueDate;
        this.gatewayAccountId = gatewayAccountId;
        this.amount = amount;
        this.netAmount = netAmount;
        this.reason = reason;
    }
}
