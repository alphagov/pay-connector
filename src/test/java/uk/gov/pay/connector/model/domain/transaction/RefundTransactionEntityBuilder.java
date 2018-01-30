package uk.gov.pay.connector.model.domain.transaction;

import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class RefundTransactionEntityBuilder {
    protected RefundStatus status = RefundStatus.CREATED;
    private String refundExternalId = RandomIdGenerator.newId();
    private String userExternalId = "someUserExternalId";
    private Long amount = 123L;
    private String refundReference = RandomIdGenerator.newId();
    private ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));

    private RefundTransactionEntityBuilder() {
    }

    public static RefundTransactionEntityBuilder aRefundTransactionEntityBuilder() {
        return new RefundTransactionEntityBuilder();
    }

    public static RefundTransactionEntityBuilder aRefundTransactionEntity() {
        return new RefundTransactionEntityBuilder();
    }

    public RefundTransactionEntityBuilder withStatus(RefundStatus status) {
        this.status = status;
        return this;
    }

    public RefundTransactionEntityBuilder withRefundExternalId(String refundExternalId) {
        this.refundExternalId = refundExternalId;
        return this;
    }

    public RefundTransactionEntityBuilder withUserExternalId(String userExternalId) {
        this.userExternalId = userExternalId;
        return this;
    }

    public RefundTransactionEntityBuilder withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public RefundTransactionEntityBuilder withRefundReference(String refundReference) {
        this.refundReference = refundReference;
        return this;
    }

    public RefundTransactionEntityBuilder withCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public RefundTransactionEntity build() {
        RefundTransactionEntity refundTransactionEntity = new RefundTransactionEntity();
        refundTransactionEntity.setStatus(status);
        refundTransactionEntity.setRefundExternalId(refundExternalId);
        refundTransactionEntity.setUserExternalId(userExternalId);
        refundTransactionEntity.setAmount(amount);
        refundTransactionEntity.setRefundReference(refundReference);
        refundTransactionEntity.setCreatedDate(createdDate);
        return refundTransactionEntity;
    }
}
