package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

public class CancelRequest {

    private ChargeEntity charge;

    private CancelRequest(ChargeEntity charge) {
        this.charge = charge;
    }

    public static CancelRequest valueOf(ChargeEntity charge) {
        return new CancelRequest(charge);
    }

    public String getTransactionId() {
        return charge.getGatewayTransactionId();
    }

    public GatewayAccountEntity getGatewayAccount() {
        return charge.getGatewayAccount();
    }
}
