package uk.gov.pay.connector.fee.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeType;

public record Fee (
    @JsonProperty("fee_type")
    FeeType feeType,

    Long amount
) {
    public static Fee from(FeeEntity feeEntity) {
        return new Fee(feeEntity.getFeeType(), feeEntity.getAmountCollected());
    }

    public static Fee of(FeeType feeType, Long amount) {
        return new Fee(feeType, amount);
    }
}
