package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RefundRequest {

    @JsonProperty("amount")
    private long amount;

    @JsonProperty("refund_amount_available")
    private long amountAvailableForRefund;

    public RefundRequest() {}

    public RefundRequest(long amount, long amountAvailableForRefund) {
        this.amount = amount;
        this.amountAvailableForRefund = amountAvailableForRefund;
    }

    public long getAmount() {
        return amount;
    }

    public long getAmountAvailableForRefund() {
        return amountAvailableForRefund;
    }

}
