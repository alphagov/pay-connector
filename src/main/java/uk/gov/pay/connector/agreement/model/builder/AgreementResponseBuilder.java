package uk.gov.pay.connector.agreement.model.builder;

import uk.gov.pay.connector.agreement.model.AgreementResponse;

import java.time.Instant;

public class AgreementResponseBuilder {

    private String agreementId;
    private Instant createdDate;
    private String reference;
    private String description;
    private String userIdentifier;
    private String serviceId;
    private boolean live;

    public String getAgreementId() {
        return agreementId;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public String getReference() {
        return reference;
    }

    public String getServiceId() {
        return serviceId;
    }

    public boolean isLive() {
        return live;
    }

    public String getDescription() {
        return description;
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public AgreementResponseBuilder withAgreementId(String agreementId) {
        this.agreementId = agreementId;
        return this;
    }

    public AgreementResponseBuilder withCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public AgreementResponseBuilder withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public AgreementResponseBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public AgreementResponseBuilder withUserIdentifier(String userIdentifier) {
        this.userIdentifier = userIdentifier;
        return this;
    }

    public AgreementResponseBuilder withServiceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public AgreementResponseBuilder withLive(boolean live) {
        this.live = live;
        return this;
    }

    public AgreementResponse build() {
        return new AgreementResponse(this);
    }
}
