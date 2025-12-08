package uk.gov.pay.connector.agreement.model;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

import java.time.Instant;

public class AgreementEntityFixture {

    private String externalId = "an-agreement-external-id";
    private GatewayAccountEntity gatewayAccount;
    private Instant createdDate = Instant.now();
    private String reference = "an-agreement-reference";
    private String description = "an-agreement-description";
    private String userIdentifier;
    private String serviceId = "a-service-id";
    private boolean live;
    private PaymentInstrumentEntity paymentInstrument;

    public static AgreementEntityFixture anAgreementEntity() {
        return new AgreementEntityFixture();
    }

    public AgreementEntityFixture withExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public AgreementEntityFixture withGatewayAccount(GatewayAccountEntity gatewayAccountEntity) {
        this.gatewayAccount = gatewayAccountEntity;
        return this;
    }

    public AgreementEntityFixture withCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public AgreementEntityFixture withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public AgreementEntityFixture withDescription(String description) {
        this.description = description;
        return this;
    }

    public AgreementEntityFixture withUserIdentifier(String userIdentifier) {
        this.userIdentifier = userIdentifier;
        return this;
    }

    public AgreementEntityFixture withServiceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public AgreementEntityFixture withLive(boolean live) {
        this.live = live;
        return this;
    }

    public AgreementEntityFixture withPaymentInstrument(PaymentInstrumentEntity paymentInstrument) {
        this.paymentInstrument = paymentInstrument;
        return this;
    }

    public AgreementEntity build() {
        var agreementEntity = new AgreementEntity(gatewayAccount, serviceId, reference, description, userIdentifier, live, createdDate, paymentInstrument);
        agreementEntity.setExternalId(externalId);
        return agreementEntity;
    }

}
