package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;

public class RefundGatewayRequest implements GatewayRequest {

    private final GatewayAccountEntity gatewayAccountEntity;
    private final String amount;
    private final String transactionId;
    private final String reference;

    private RefundGatewayRequest(String transactionId, GatewayAccountEntity gatewayAccount, String amount, String reference) {
        this.transactionId = transactionId;
        this.gatewayAccountEntity = gatewayAccount;
        this.amount = amount;
        this.reference = reference;
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

    public static RefundGatewayRequest valueOf(RefundEntity refundEntity) {
        return new RefundGatewayRequest(
                refundEntity.getChargeEntity().getGatewayTransactionId(),
                refundEntity.getChargeEntity().getGatewayAccount(),
                String.valueOf(refundEntity.getAmount()),
                refundEntity.getExternalId());
    }

    public String getReference() {
        return reference;
    }
}
