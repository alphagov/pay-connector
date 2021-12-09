package uk.gov.pay.connector.fee.model;

import uk.gov.pay.connector.charge.model.domain.FeeType;

public class Fee {

    private FeeType feeType;

    private Long amount;

    private Fee(FeeType feeType, Long amount) {
        this.feeType = feeType;
        this.amount = amount;
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
}
