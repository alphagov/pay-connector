package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;

public class RecoupFeeRequest {
    private final ChargeEntity charge;
    private final FeeEntity fee;

    public static RecoupFeeRequest of(ChargeEntity charge, FeeEntity fee) {
        return new RecoupFeeRequest(charge, fee);
    }
    
    private RecoupFeeRequest(ChargeEntity charge, FeeEntity fee) {
        this.charge = charge;
        this.fee = fee;
    }

    public ChargeEntity getCharge() {
        return charge;
    }

    public FeeEntity getFee() {
        return fee;
    }
}
