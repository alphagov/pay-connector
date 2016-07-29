package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RefundRequest {

    @JsonProperty("amount")
    private long amount;

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }
}
