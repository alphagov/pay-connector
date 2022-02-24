package uk.gov.pay.connector.queue.tasks.dispute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BalanceTransaction {

    @JsonProperty("amount")
    private Long amount;
    @JsonProperty("fee")
    private Long fee;
    @JsonProperty("net")
    private Long netAmount;

    public Long getAmount() {
        return amount;
    }

    public Long getFee() {
        return fee;
    }

    public Long getNetAmount() {
        return netAmount;
    }
}
