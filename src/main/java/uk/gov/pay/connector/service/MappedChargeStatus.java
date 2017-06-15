package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.domain.ChargeStatus;

import static uk.gov.pay.connector.service.InterpretedStatus.Type.CHARGE_STATUS;

public class MappedChargeStatus implements InterpretedStatus {

    private final ChargeStatus status;

    public MappedChargeStatus(ChargeStatus status) {
        this.status = status;
    }

    @Override
    public Type getType() {
        return CHARGE_STATUS;
    }

    @Override
    public ChargeStatus getChargeStatus() {
        return status;
    }

}
