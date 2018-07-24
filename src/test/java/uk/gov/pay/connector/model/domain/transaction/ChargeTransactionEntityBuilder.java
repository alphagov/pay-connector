package uk.gov.pay.connector.model.domain.transaction;

import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class ChargeTransactionEntityBuilder {
    private String gatewayTransactionId = "someGatewayTransactionId";
    private Long amount = 1234L;
    private ChargeStatus status = ChargeStatus.CREATED;
    private String email = "email@example.com";
    private ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));

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

    public ChargeTransactionEntityBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public ChargeTransactionEntityBuilder withCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public ChargeTransactionEntity build() {
        ChargeTransactionEntity transactionEntity = new ChargeTransactionEntity();
        transactionEntity.setGatewayTransactionId(gatewayTransactionId);
        transactionEntity.setAmount(amount);
        transactionEntity.updateStatus(status);
        transactionEntity.setEmail(email);
        transactionEntity.setCreatedDate(createdDate);
        return transactionEntity;
    }
}
