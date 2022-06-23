package uk.gov.pay.connector.events.eventdetails.dispute;

public class DisputeCreatedEventDetails extends DisputeEventDetails {
    private final Long fee;
    private final Long evidenceDueDate;
    private final Long amount;
    private final Long netAmount;
    private final String reason;

    public DisputeCreatedEventDetails(Long fee, Long evidenceDueDate, String gatewayAccountId, Long amount, Long netAmount, String reason) {
        super(gatewayAccountId);
        this.fee = fee;
        this.evidenceDueDate = evidenceDueDate;
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
