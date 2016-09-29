package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


// FIXME this Json ignore is for backward compatibilty for Jira PP-1224, to be removed at a later state
@JsonIgnoreProperties(ignoreUnknown = true)
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
