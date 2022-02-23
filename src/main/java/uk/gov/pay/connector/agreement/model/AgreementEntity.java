package uk.gov.pay.connector.agreement.model;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.service.payments.commons.jpa.InstantToUtcTimestampWithoutTimeZoneConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.Instant;

public class AgreementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "agreements_agreement_id_seq")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "gateway_account_id", updatable = false)
    private GatewayAccountEntity gatewayAccount;

    @Column(name="external_id")
    private String externalId;

    @Column(name = "created_date")
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    private Instant createdDate;

    @Column(name = "reference")
    private String reference;
    
    @Column(name = "service_id")
    private String serviceId;
    
    @Column(name = "live")
    private boolean live;

    public AgreementEntity() {
        // For JPA
    }
    
    private AgreementEntity(GatewayAccountEntity gatewayAccount, String serviceId, String reference,
                            boolean live, Instant createdDate) {
        AgreementEntity agreementEntity = new AgreementEntity();
        agreementEntity.externalId = RandomIdGenerator.newId();
        agreementEntity.gatewayAccount = gatewayAccount;
        agreementEntity.serviceId = serviceId;
        agreementEntity.reference = reference;
        agreementEntity.live = live;
        agreementEntity.createdDate = createdDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
    }

    public void setGatewayAccount(GatewayAccountEntity gatewayAccount) {
        this.gatewayAccount = gatewayAccount;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public boolean isLive() {
        return live;
    }

    public void setLive(boolean live) {
        this.live = live;
    }
    
    public static class AgreementEntityBuilder {
        private GatewayAccountEntity gatewayAccount;
        private Instant createdDate;
        private String reference;
        private String serviceId;
        private boolean live;

        public static AgreementEntityBuilder anAgreementEntity(Instant createdDate) {
            var agreementEntityBuilder = new AgreementEntityBuilder();
            agreementEntityBuilder.withCreatedDate(createdDate);
            return agreementEntityBuilder;
        }
        
        public AgreementEntityBuilder withGatewayAccount(GatewayAccountEntity gatewayAccountEntity) {
            this.gatewayAccount = gatewayAccountEntity;
            return this;
        }

        public AgreementEntityBuilder withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public AgreementEntityBuilder withReference(String reference) {
            this.reference = reference;
            return this;
        }

        public AgreementEntityBuilder withServiceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public AgreementEntityBuilder withLive(boolean live) {
            this.live = live;
            return this;
        }

        public AgreementEntity build() {
            return new AgreementEntity(gatewayAccount, serviceId, reference, live, createdDate);
        }
        
    }
    
}
