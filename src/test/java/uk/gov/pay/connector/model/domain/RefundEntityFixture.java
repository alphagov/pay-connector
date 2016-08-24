package uk.gov.pay.connector.model.domain;

import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

public class RefundEntityFixture {
    private Long amount = 500L;
    private RefundStatus status = RefundStatus.CREATED;
    private GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();

    public static RefundEntityFixture aValidRefundEntity() {
        return new RefundEntityFixture();
    }

    public RefundEntity build() {
        RefundEntity refundEntity = new RefundEntity(buildChargeEntity(), amount);
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

    public RefundEntityFixture withGatewayAccountEntity(GatewayAccountEntity gatewayAccountEntity) {
        this.gatewayAccountEntity = gatewayAccountEntity;
        return this;
    }

    public Long getAmount() {
        return amount;
    }

    private ChargeEntity buildChargeEntity() {
        return aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).build();
    }
}
