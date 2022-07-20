package uk.gov.pay.connector.events.eventdetails.dispute;

public class DisputeLostEventDetails extends DisputeEventDetails {
    private final Long netAmount;
    private final Long amount;
    private final Long fee;

    public DisputeLostEventDetails(String gatewayAccountId, Long amount, Long netAmount, Long fee) {
        super(gatewayAccountId);
        this.netAmount = netAmount;
        this.amount = amount;
        this.fee = fee;
    }
    
    public DisputeLostEventDetails(String gatewayAccountId, Long amount) {
        this(gatewayAccountId, amount, null, null);
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
