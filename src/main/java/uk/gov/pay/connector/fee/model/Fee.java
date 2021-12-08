package uk.gov.pay.connector.fee.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.charge.model.domain.FeeType;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Fee {

    @JsonProperty("fee_type")
    private FeeType feeType;

    private Long amount;

    private Fee(FeeType feeType, Long amount) {
        this.feeType = feeType;
        this.amount = amount;
    }

    public static Fee of(FeeType feeType, Long amount) {
        return new Fee(feeType, amount);
    }

    public static Fee of(FeeType feeType, int amount) {
        return new Fee(feeType, Long.valueOf(amount));
    }

    public FeeType getFeeType() {
        return feeType;
    }

    public Long getAmount() {
        return amount;
    }
}
