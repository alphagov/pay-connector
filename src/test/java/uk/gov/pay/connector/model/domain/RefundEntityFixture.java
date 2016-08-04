package uk.gov.pay.connector.model.domain;

import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

public class RefundEntityFixture {
    private ChargeEntity chargeEntity = defaultChargeEntity();
    private Long amount = 500L;
    private RefundStatus status = RefundStatus.CREATED;

    public static RefundEntityFixture aValidRefundEntity() {
        return new RefundEntityFixture();
    }

    public RefundEntity build() {
        RefundEntity refundEntity = new RefundEntity(chargeEntity, amount);
        refundEntity.setStatus(status);
        return refundEntity;
    }

    public RefundEntityFixture withStatus(RefundStatus status) {
        this.status = status;
        return this;
    }

    public RefundEntityFixture withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public ChargeEntity getChargeEntity() {
        return chargeEntity;
    }

    public Long getAmount() {
        return amount;
    }

    public ChargeEntity defaultChargeEntity() {
        return aValidChargeEntity().build();
    }
}
