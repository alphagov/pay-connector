package uk.gov.pay.connector.agreement.model;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.service.payments.commons.jpa.InstantToUtcTimestampWithoutTimeZoneConverter;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "agreements")
@Access(AccessType.FIELD)
@SequenceGenerator(name = "agreements_id_seq",
        sequenceName = "agreements_id_seq", allocationSize = 1)
public class AgreementEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "agreements_id_seq")
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

    @Column(name = "description")
    private String description;

    @Column(name = "user_identifier")
    private String userIdentifier;
    
    @Column(name = "service_id")
    private String serviceId;
    
    @Column(name = "live")
    private boolean live;

    @OneToOne
    @JoinColumn(name = "payment_instrument_id", nullable = true)
    private PaymentInstrumentEntity paymentInstrument;
    

    public AgreementEntity() {
        // For JPA
    }
    
    private AgreementEntity(GatewayAccountEntity gatewayAccount, String serviceId, String reference,
                            String description, String userIdentifier, boolean live, Instant createdDate) {
        this.externalId = RandomIdGenerator.newId();
        this.gatewayAccount = gatewayAccount;
        this.serviceId = serviceId;
        this.reference = reference;
        this.live = live;
        this.createdDate = createdDate;
        this.description = description;
        this.userIdentifier = userIdentifier;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public void setUserIdentifier(String userIdentifier) {
        this.userIdentifier = userIdentifier;
    }

    public Optional<PaymentInstrumentEntity> getPaymentInstrument() {
        return Optional.ofNullable(paymentInstrument);
    }

    public void setPaymentInstrument(PaymentInstrumentEntity paymentInstrumentEntity) {
        this.paymentInstrument = paymentInstrumentEntity;
    }

    public static class AgreementEntityBuilder {
        private GatewayAccountEntity gatewayAccount;
        private Instant createdDate;
        private String reference;
        private String description;
        private String userIdentifier;
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

        public AgreementEntityBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public AgreementEntityBuilder withUserIdentifier(String userIdentifier) {
            this.userIdentifier = userIdentifier;
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
            return new AgreementEntity(gatewayAccount, serviceId, reference, description, userIdentifier, live, createdDate);
        }
        
    }
    
}
