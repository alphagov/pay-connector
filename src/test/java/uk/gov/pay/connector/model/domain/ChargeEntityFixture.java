package uk.gov.pay.connector.model.domain;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;

public class ChargeEntityFixture {

    private Long id = 1L;
    private Long amount = 500L;
    private String returnUrl = "http://return.com";
    private String email = "test@email.com";
    private String description = "This is a description";
    private String reference = "This is a reference";
    private ChargeStatus status = ChargeStatus.CREATED;
    private GatewayAccountEntity gatewayAccountEntity = defaultGatewayAccountEntity();
    private String transactionId;
    private ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));

    public static ChargeEntityFixture aValidChargeEntity() {
        return new ChargeEntityFixture();
    }

    public ChargeEntity build() {
        ChargeEntity chargeEntity = new ChargeEntity(amount, status ,returnUrl, description, reference, gatewayAccountEntity, email, createdDate);
        chargeEntity.setId(id);
        chargeEntity.setGatewayTransactionId(transactionId);
        return chargeEntity;
    }

    public ChargeEntityFixture withId(Long id) {
        this.id = id;
        return this;
    }

    public ChargeEntityFixture withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public ChargeEntityFixture withGatewayAccountEntity(GatewayAccountEntity gatewayAccountEntity) {
        this.gatewayAccountEntity = gatewayAccountEntity;
        return this;
    }

    public ChargeEntityFixture withTransactionId(String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    public ChargeEntityFixture withReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
        return this;
    }

    public ChargeEntityFixture withDescription(String description) {
        this.description = description;
        return this;
    }

    public ChargeEntityFixture withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public ChargeEntityFixture withStatus(ChargeStatus status) {
        this.status = status;
        return this;
    }

    public ChargeEntityFixture withCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    private GatewayAccountEntity defaultGatewayAccountEntity() {
        GatewayAccountEntity accountEntity = new GatewayAccountEntity("provider", new HashMap<>());

        accountEntity.setId(1L);
        accountEntity.setServiceName("MyService");
        EmailNotificationEntity emailNotificationEntity = new EmailNotificationEntity(accountEntity, "template body");
        accountEntity.setEmailNotification(emailNotificationEntity);

        return accountEntity;
    }
}
