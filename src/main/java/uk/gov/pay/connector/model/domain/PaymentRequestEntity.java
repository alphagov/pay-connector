package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.model.ServicePaymentReference;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;


@Entity
@Table(name = "payment_requests")
@Access(AccessType.FIELD)
@SequenceGenerator(name = "payment_requests_id_seq",
        sequenceName = "payment_requests_id_seq", allocationSize = 1)
public class PaymentRequestEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_requests_id_seq")
    @JsonIgnore
    private Long id;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "return_url")
    private String returnUrl;

    @ManyToOne
    @JoinColumn(name = "gateway_account_id", updatable = false)
    private GatewayAccountEntity gatewayAccount;

    @Column(name = "description")
    private String description;

    @Column(name = "reference")
    @Convert(converter = ServicePaymentReferenceConverter.class)
    private ServicePaymentReference reference;

    @Column(name = "created_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;

    @Column(name = "external_id")
    private String externalId;

    public PaymentRequestEntity() {
        // enjoy it JPA
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
    }

    public void setGatewayAccount(GatewayAccountEntity gatewayAccount) {
        this.gatewayAccount = gatewayAccount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ServicePaymentReference getReference() {
        return reference;
    }

    public void setReference(ServicePaymentReference reference) {
        this.reference = reference;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }


    public static PaymentRequestEntity from(ChargeEntity chargeEntity) {
        PaymentRequestEntity paymentEntity = new PaymentRequestEntity();
        paymentEntity.setAmount(chargeEntity.getAmount());
        paymentEntity.setCreatedDate(chargeEntity.getCreatedDate());
        paymentEntity.setDescription(chargeEntity.getDescription());
        paymentEntity.setExternalId(chargeEntity.getExternalId());
        paymentEntity.setGatewayAccount(chargeEntity.getGatewayAccount());
        paymentEntity.setReference(chargeEntity.getReference());
        paymentEntity.setReturnUrl(chargeEntity.getReturnUrl());

        return paymentEntity;
    }
}
