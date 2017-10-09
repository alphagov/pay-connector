package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.defaultGatewayAccountEntity;

public class PaymentRequestEntityFixture {

    private String externalId = RandomIdGenerator.newId();
    private Long amount = 500L;
    private String returnUrl = "http://return.com";
    private String description = "This is a description";
    private String reference = "This is a reference";
    private GatewayAccountEntity gatewayAccountEntity = defaultGatewayAccountEntity();
    private ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));

    public static PaymentRequestEntityFixture aValidPaymentRequestEntity() {
        return new PaymentRequestEntityFixture();
    }

    public PaymentRequestEntity build() {
        PaymentRequestEntity entity = new PaymentRequestEntity();
        entity.setReturnUrl(this.returnUrl);
        entity.setReference(this.reference);
        entity.setGatewayAccount(this.gatewayAccountEntity);
        entity.setExternalId(this.externalId);
        entity.setDescription(this.description);
        entity.setCreatedDate(this.createdDate);
        entity.setAmount(this.amount);

        return entity;
    }

    public PaymentRequestEntityFixture withGatewayAccountEntity(GatewayAccountEntity gatewayAccount) {
        this.gatewayAccountEntity = gatewayAccount;
        return this;
    }
}
