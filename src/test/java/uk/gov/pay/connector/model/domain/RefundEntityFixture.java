package uk.gov.pay.connector.model.domain;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

public class RefundEntityFixture {

    private Long amount = 500L;
    private RefundStatus status = RefundStatus.CREATED;
    private GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
    private ChargeEntity charge;
    private String reference = "reference";
    public static String userExternalId = "AA213FD51B3801043FBC";
    private String externalId = "someExternalId";
    private ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));

    public static RefundEntityFixture aValidRefundEntity() {
        return new RefundEntityFixture();
    }

    public RefundEntity build() {
        ChargeEntity chargeEntity = charge == null ? buildChargeEntity() : charge;
        RefundEntity refundEntity = new RefundEntity(chargeEntity, amount, userExternalId);
        refundEntity.setStatus(status);
        refundEntity.setReference(reference);
        refundEntity.setExternalId(externalId);
        refundEntity.setUserExternalId(userExternalId);
        refundEntity.setCreatedDate(createdDate);
        return refundEntity;
    }

    public RefundEntityFixture withStatus(RefundStatus status) {
        this.status = status;
        return this;
    }

    public RefundEntityFixture withCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }
    
    public RefundEntityFixture withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public RefundEntityFixture withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public RefundEntityFixture withGatewayAccountEntity(GatewayAccountEntity gatewayAccountEntity) {
        this.gatewayAccountEntity = gatewayAccountEntity;
        return this;
    }

    public RefundEntityFixture withUserExternalId(String userExternalId) {
        this.userExternalId = userExternalId;
        return this;
    }
    
    public Long getAmount() {
        return amount;
    }

    private ChargeEntity buildChargeEntity() {
        return aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).build();
    }

    public RefundEntityFixture withCharge(ChargeEntity charge) {
        this.charge = charge;
        return this;
    }

    public RefundEntityFixture withExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }
}
