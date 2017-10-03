package uk.gov.pay.connector.model.spike;

import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;

import java.time.ZonedDateTime;
import java.util.HashMap;
import uk.gov.pay.connector.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.spike.TransactionEntity.TransactionOperation;

public class PaymentRequestEntityFixture {

  private GatewayAccountEntity gatewayAccountEntity = defaultGatewayAccountEntity();
  private String returnUrl = "http://return.com";
  private String description = "This is a description";
  private String reference = "This is a reference";
  private String externalId = "externalId";

  public PaymentRequestEntity build() {
    PaymentRequestEntity paymentRequestEntity = new PaymentRequestEntity(externalId, 10L, reference, description, returnUrl, gatewayAccountEntity);
    return paymentRequestEntity;
  }
  public static PaymentRequestEntityFixture aValidPaymentRequestEntity() {
    return new PaymentRequestEntityFixture();
  }
  public PaymentRequestEntityFixture withReturnUrl(String returnUrl) {
    this.returnUrl = returnUrl;
    return this;
  }

  public PaymentRequestEntityFixture withDescription(String description) {
    this.description = description;
    return this;
  }
  public PaymentRequestEntityFixture withGatewayAccount(GatewayAccountEntity gatewayAccount) {
    this.gatewayAccountEntity = gatewayAccount;
    return this;
  }
  public PaymentRequestEntityFixture withReference(String reference) {
    this.reference = reference;
    return this;
  }

  public GatewayAccountEntity getGatewayAccountEntity() {
    return gatewayAccountEntity;
  }

  public String getReturnUrl() {
    return returnUrl;
  }

  public String getDescription() {
    return description;
  }

  public String getReference() {
    return reference;
  }


  static PaymentRequestEntity defaultPaymentRequestEntity() {
    return new PaymentRequestEntity("external_id", 10L, "reference", "description", "return_url", defaultGatewayAccountEntity());
  }

  private static GatewayAccountEntity defaultGatewayAccountEntity() {
    GatewayAccountEntity accountEntity = new GatewayAccountEntity("sandbox", new HashMap<>(), TEST);
    accountEntity.setId(1L);
    accountEntity.setServiceName("MyService");
    EmailNotificationEntity emailNotificationEntity = new EmailNotificationEntity(accountEntity);
    emailNotificationEntity.setTemplateBody("template body");
    accountEntity.setEmailNotification(emailNotificationEntity);
    return accountEntity;
  }
}
