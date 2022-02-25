package uk.gov.pay.connector.events.eventdetails.dispute;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class DisputeCreatedEventDetails extends EventDetails {
    private final Long fee;
    private final Long evidenceDueDate;
    private final String gatewayAccountId;
    private final Long amount;
    private final Long netAmount;
    private final String reason;

    public DisputeCreatedEventDetails(Long fee, Long evidenceDueDate, String gatewayAccountId, Long amount, Long netAmount, String reason) {
        this.fee = fee;
        this.evidenceDueDate = evidenceDueDate;
        this.gatewayAccountId = gatewayAccountId;
        this.amount = amount;
        this.netAmount = netAmount;
        this.reason = reason;
    }

    public Long getFee() {
        return fee;
    }

    public Long getEvidenceDueDate() {
        return evidenceDueDate;
    }

    public String getGatewayAccountId() {
        return gatewayAccountId;
    }

    public Long getAmount() {
        return amount;
    }

    public Long getNetAmount() {
        return netAmount;
    }

    public String getReason() {
        return reason;
    }
}
