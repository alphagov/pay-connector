package uk.gov.pay.connector.events.eventdetails.dispute;

public class DisputeLostEventDetails extends DisputeEventDetails {
    private final Long netAmount;
    private final Long amount;
    private final Long fee;

    public DisputeLostEventDetails(String gatewayAccountId, Long netAmount, Long amount, Long fee) {
        super(gatewayAccountId);
        this.netAmount = netAmount;
        this.amount = amount;
        this.fee = fee;
    }

    public Long getNetAmount() {
        return netAmount;
    }

    public Long getAmount() {
        return amount;
    }

    public Long getFee() {
        return fee;
    }
}
