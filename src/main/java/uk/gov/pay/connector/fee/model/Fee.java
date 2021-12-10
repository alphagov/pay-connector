package uk.gov.pay.connector.fee.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeType;

import java.util.Objects;

public class Fee {

    @JsonProperty("fee_type")
    private FeeType feeType;

    private Long amount;

    private Fee(FeeType feeType, Long amount) {
        this.feeType = feeType;
        this.amount = amount;
    }
    
    public static Fee from(FeeEntity feeEntity) {
        return new Fee(feeEntity.getFeeType(), feeEntity.getAmountCollected());
    }

    public static Fee of(FeeType feeType, Long amount) {
        return new Fee(feeType, amount);
    }

    public FeeType getFeeType() {
        return feeType;
    }

    public Long getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Fee fee = (Fee) o;
        return feeType == fee.feeType && Objects.equals(amount, fee.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feeType, amount);
    }
}
