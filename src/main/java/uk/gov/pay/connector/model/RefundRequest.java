package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RefundRequest {

    @JsonProperty("amount")
    private int amount;

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
}
