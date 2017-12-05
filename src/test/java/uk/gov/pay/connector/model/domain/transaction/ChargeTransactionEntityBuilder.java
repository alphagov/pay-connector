package uk.gov.pay.connector.model.domain.transaction;

import uk.gov.pay.connector.model.domain.ChargeStatus;

public final class ChargeTransactionEntityBuilder {
    private String gatewayTransactionId = "someGatewayTransactionId";
    private Long amount = 1234L;
    private ChargeStatus status = ChargeStatus.CREATED;

    private ChargeTransactionEntityBuilder() {
    }

    public static ChargeTransactionEntityBuilder aChargeTransactionEntity() {
        return new ChargeTransactionEntityBuilder();
    }

    public ChargeTransactionEntityBuilder withGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
        return this;
    }

    public ChargeTransactionEntityBuilder withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public ChargeTransactionEntityBuilder withStatus(ChargeStatus status) {
        this.status = status;
        return this;
    }

    public ChargeTransactionEntity build() {
        ChargeTransactionEntity transactionEntity = new ChargeTransactionEntity();
        transactionEntity.setGatewayTransactionId(gatewayTransactionId);
        transactionEntity.setAmount(amount);
        transactionEntity.updateStatus(status);
        return transactionEntity;
    }
}
