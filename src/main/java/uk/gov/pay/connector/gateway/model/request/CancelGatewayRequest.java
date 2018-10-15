package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gateway.GatewayOperation;

public class CancelGatewayRequest implements GatewayRequest {

    private ChargeEntity charge;

    private CancelGatewayRequest(ChargeEntity charge) {
        this.charge = charge;
    }

    public static CancelGatewayRequest valueOf(ChargeEntity charge) {
        return new CancelGatewayRequest(charge);
    }

    public String getTransactionId() {
        return charge.getGatewayTransactionId();
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return charge.getGatewayAccount();
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.CANCEL;
    }
}
