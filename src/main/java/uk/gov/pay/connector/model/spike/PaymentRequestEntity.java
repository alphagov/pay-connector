package uk.gov.pay.connector.model.spike;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import uk.gov.pay.connector.model.domain.AbstractEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import uk.gov.pay.connector.model.domain.RefundEntity;

@Entity
@Table(name = "payment_requests")
@SequenceGenerator(name = "payment_requests_payment_id_seq", sequenceName = "payment_requests_payment_id_seq", allocationSize = 1)
@Access(AccessType.FIELD)
public class PaymentRequestEntity extends AbstractEntity {
    @Column(name = "external_id")
    private String externalId;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "reference")
    private String reference;

    @Column(name = "description")
    private String description;

    @Column(name = "return_url")
    private String returnUrl;

    public PaymentRequestEntity(String externalId, Long amount, String reference,
        String description, String returnUrl,
        GatewayAccountEntity gatewayAccount) {
        this.externalId = externalId;
        this.amount = amount;
        this.reference = reference;
        this.description = description;
        this.returnUrl = returnUrl;
        this.gatewayAccount = gatewayAccount;
    }

    public PaymentRequestEntity() {
        //for jpa
    }

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "gateway_account_id", updatable = false)
    private GatewayAccountEntity gatewayAccount;

    @OneToMany(mappedBy = "paymentRequest")
    private List<TransactionEntity> transactions = new ArrayList<>();

    public String getExternalId() {
        return externalId;
    }

    public Long getAmount() {
        return amount;
    }

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
    }
}
