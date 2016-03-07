package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccount;

public class CaptureRequest {

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

    public GatewayAccount getGatewayAccount() {
        return GatewayAccount.valueOf(charge.getGatewayAccount());
    }

    public static CaptureRequest valueOf(ChargeEntity charge) {
        return new CaptureRequest(charge);
    }
}
