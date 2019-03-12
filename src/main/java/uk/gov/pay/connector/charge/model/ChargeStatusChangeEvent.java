package uk.gov.pay.connector.charge.model;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

public class ChargeStatusChangeEvent {
    private final ChargeStatus chargeStatus;
    
    public static ChargeStatusChangeEvent from(ChargeEntity chargeEntity) {
        return new ChargeStatusChangeEvent(ChargeStatus.fromString(chargeEntity.getStatus()));
    }

    ChargeStatusChangeEvent(ChargeStatus chargeStatus) {
        this.chargeStatus = chargeStatus;
    }

    public ChargeStatus getChargeStatus() {
        return chargeStatus;
    }
}
