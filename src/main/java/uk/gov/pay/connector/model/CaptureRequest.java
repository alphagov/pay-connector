package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

public class CaptureRequest implements GatewayRequest {

    private ChargeEntity charge;

    private CaptureRequest(ChargeEntity charge) {
        this.charge = charge;
    }

    public String getAmount() {
        return String.valueOf(charge.getAmount());
    }

    public String getTransactionId() {
        return charge.getGatewayTransactionId();
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return charge.getGatewayAccount();
    }

    public static CaptureRequest valueOf(ChargeEntity charge) {
        return new CaptureRequest(charge);
    }
}
