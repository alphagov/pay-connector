package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.model.domain.transaction.TransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionOperation;

public final class TransactionEntityBuilder {
    private String gatewayTransactionId = "someGatewayTransactionId";
    private Long amount = 1234L;
    private String refundExternalId = "someRefundExternalId";
    private ChargeStatus status = ChargeStatus.CREATED;
    private String userExternalId = "someUserExternalId";
    private TransactionOperation operation = TransactionOperation.CHARGE;

    private TransactionEntityBuilder() {
    }

    public static TransactionEntityBuilder aTransactionEntity() {
        return new TransactionEntityBuilder();
    }

    public TransactionEntityBuilder withGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
        return this;
    }

    public TransactionEntityBuilder withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public TransactionEntityBuilder withRefundExternalId(String refundExternalId) {
        this.refundExternalId = refundExternalId;
        return this;
    }

    public TransactionEntityBuilder withStatus(ChargeStatus status) {
        this.status = status;
        return this;
    }

    public TransactionEntityBuilder withUserExternalId(String userExternalId) {
        this.userExternalId = userExternalId;
        return this;
    }

    public TransactionEntityBuilder withOperation(TransactionOperation operation) {
        this.operation = operation;
        return this;
    }

    public TransactionEntity build() {
        TransactionEntity transactionEntity = new TransactionEntity();
        transactionEntity.setGatewayTransactionId(gatewayTransactionId);
        transactionEntity.setAmount(amount);
        transactionEntity.setRefundExternalId(refundExternalId);
        transactionEntity.setStatus(status);
        transactionEntity.setUserExternalId(userExternalId);
        transactionEntity.setOperation(operation);
        return transactionEntity;
    }
}
