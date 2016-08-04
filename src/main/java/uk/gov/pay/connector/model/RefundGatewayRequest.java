package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

public class RefundGatewayRequest implements GatewayRequest {

    private final GatewayAccountEntity gatewayAccountEntity;
    private final String amount;
    private final String transactionId;

    private RefundGatewayRequest(ChargeEntity charge) {
        this(charge, String.valueOf(charge.getAmount()));
    }

    private RefundGatewayRequest(ChargeEntity charge, long partialAmount) {
        this(charge, String.valueOf(partialAmount));
    }

    private RefundGatewayRequest(ChargeEntity charge, String amount) {
        this.transactionId = charge.getGatewayTransactionId();
        this.gatewayAccountEntity = charge.getGatewayAccount();
        this.amount = amount;
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

    public static RefundGatewayRequest valueOf(ChargeEntity charge, long partialAmount) {
        return new RefundGatewayRequest(charge, partialAmount);
    }
}
