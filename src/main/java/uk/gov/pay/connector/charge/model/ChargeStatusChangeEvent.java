package uk.gov.pay.connector.charge.model;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

public class ChargeStatusChangeEvent {
    public static ChargeStatusChangeEvent from(ChargeEntity chargeEntity) {
        return new ChargeStatusChangeEvent();
    }
}
