package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionOperation;

public final class ChargeTransactionEntityBuilder {
    private String gatewayTransactionId = "someGatewayTransactionId";
    private Long amount = 1234L;
    private ChargeStatus status = ChargeStatus.CREATED;
    private TransactionOperation operation = TransactionOperation.CHARGE;

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

    public ChargeTransactionEntityBuilder withOperation(TransactionOperation operation) {
        this.operation = operation;
        return this;
    }

    public ChargeTransactionEntity build() {
        ChargeTransactionEntity transactionEntity = new ChargeTransactionEntity();
        transactionEntity.setGatewayTransactionId(gatewayTransactionId);
        transactionEntity.setAmount(amount);
        transactionEntity.updateStatus(status);
        transactionEntity.setOperation(operation);
        return transactionEntity;
    }
}
