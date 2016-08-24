package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;

public class RefundGatewayRequest implements GatewayRequest {

    private final GatewayAccountEntity gatewayAccountEntity;
    private final String amount;
    private final String transactionId;
    private final String refundReference;

    private RefundGatewayRequest(ChargeEntity charge) {
        this(charge, new RefundEntity(charge, charge.getAmount()));
    }

    private RefundGatewayRequest(ChargeEntity charge, RefundEntity refundEntity) {
        this(charge, String.valueOf(refundEntity.getAmount()), refundEntity.getExternalId());
    }

    private RefundGatewayRequest(ChargeEntity charge, String amount, String refundReference) {
        this.transactionId = charge.getGatewayTransactionId();
        this.gatewayAccountEntity = charge.getGatewayAccount();
        this.amount = amount;
        this.refundReference = refundReference;
    }

    public String getAmount() {
        return amount;
    }

    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccountEntity;
    }

    public static RefundGatewayRequest valueOf(ChargeEntity charge) {
        return new RefundGatewayRequest(charge);
    }

    public static RefundGatewayRequest valueOf(ChargeEntity charge, RefundEntity refundEntity) {
        return new RefundGatewayRequest(charge, refundEntity);
    }
}
