package uk.gov.pay.connector.gateway.model.status;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

public class MappedChargeStatus implements InterpretedStatus {

    private final ChargeStatus status;

    public MappedChargeStatus(ChargeStatus status) {
        this.status = status;
    }

    @Override
    public Type getType() {
        return Type.CHARGE_STATUS;
    }

    @Override
    public ChargeStatus getChargeStatus() {
        return status;
    }

}
