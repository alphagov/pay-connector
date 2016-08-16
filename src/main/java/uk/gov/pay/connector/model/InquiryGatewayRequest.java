package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

public class InquiryGatewayRequest implements GatewayRequest {

    private final GatewayAccountEntity gatewayAccountEntity;
    private final String transactionId;

    private InquiryGatewayRequest(ChargeEntity charge) {
        this.transactionId = charge.getGatewayTransactionId();
        this.gatewayAccountEntity = charge.getGatewayAccount();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public static InquiryGatewayRequest valueOf(ChargeEntity charge) {
        return new InquiryGatewayRequest(charge);
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccountEntity;
    }
}
